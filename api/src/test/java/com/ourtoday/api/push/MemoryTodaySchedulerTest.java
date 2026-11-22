package com.ourtoday.api.push;

import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.post.PostService;
import com.ourtoday.api.post.dto.PostCreateRequest;
import com.ourtoday.api.user.User;
import com.ourtoday.api.user.UserService;
import com.ourtoday.api.user.dto.UserSettingsUpdateRequest;
import com.ourtoday.api.user.dto.UserUpdateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 현재 시각을 2026-11-22 20:00 KST 로 고정. 이 테스트가 만든 행만 지운다 */
@SpringBootTest
@Import(MutableClock.Config.class)
class MemoryTodaySchedulerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired MemoryTodayScheduler scheduler;
    @Autowired FakePushSender fake;
    @Autowired MutableClock clock;
    @Autowired UserService userService;
    @Autowired CoupleService coupleService;
    @Autowired PostService postService;
    @Autowired PushTokenService tokenService;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean PushDispatcher dispatcher;

    private final List<UUID> userIds = new ArrayList<>();
    private UUID a;
    private UUID b;
    private UUID coupleId;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        clock.set(LocalDate.of(2026, 11, 22).atTime(20, 0).atZone(KST).toInstant());
        a = newUser("민지");
        b = newUser("지호");
        String code = coupleService.createInvite(a).inviteCode();
        coupleId = coupleService.join(b, code).id();
        tokenA = addToken(a);
        tokenB = addToken(b);
        fake.reset();
    }

    @AfterEach
    void cleanUp() {
        clock.reset();
        jdbc.update("delete from reactions where post_id in (select id from posts where couple_id = ?)", coupleId);
        jdbc.update("delete from post_images where post_id in (select id from posts where couple_id = ?)", coupleId);
        jdbc.update("delete from posts where couple_id = ?", coupleId);
        for (UUID u : userIds) {
            jdbc.update("delete from push_tokens where user_id = ?", u);
            jdbc.update("delete from couple_members where user_id = ?", u);
        }
        jdbc.update("delete from couples where id = ?", coupleId);
        for (UUID u : userIds) {
            jdbc.update("delete from user_settings where user_id = ?", u);
            jdbc.update("delete from users where id = ?", u);
        }
        userIds.clear();
    }

    private UUID newUser(String nickname) {
        User user = userService.findOrCreateByKakaoId("kakao-" + nickname + "-" + UUID.randomUUID());
        userService.updateMe(user.getId(), new UserUpdateRequest(nickname, null));
        userIds.add(user.getId());
        return user.getId();
    }

    private String addToken(UUID userId) {
        String token = "tok-" + UUID.randomUUID();
        tokenService.register(userId, token, PushPlatform.ANDROID);
        return token;
    }

    private void post(LocalDate date) {
        postService.create(a, new PostCreateRequest("비밀 일기 내용", date, List.of()));
    }

    private List<FakePushSender.Sent> sentTo(String token) {
        return fake.sent().stream().filter(s -> s.token().equals(token)).toList();
    }

    @Test
    void 작년_같은_날_기록이_있으면_둘_다에게_간다() {
        post(LocalDate.of(2025, 11, 22));
        fake.reset();

        assertThat(scheduler.sendMemoryToday()).isEqualTo(1);

        for (String token : List.of(tokenA, tokenB)) {
            assertThat(sentTo(token)).hasSize(1);
            PushMessage m = sentTo(token).get(0).message();
            assertThat(m.body()).isEqualTo("1년 전 오늘, 둘이 남긴 기록이 있어요");
            assertThat(m.path()).isEqualTo("/");
            assertThat(m.type()).isEqualTo("memory_today");
            assertThat(m.body()).doesNotContain("비밀 일기 내용");
        }
    }

    @Test
    void 이년_전만_있으면_이년_전이라고_쓴다() {
        post(LocalDate.of(2024, 11, 22));
        fake.reset();

        scheduler.sendMemoryToday();

        assertThat(sentTo(tokenA)).extracting(s -> s.message().body())
            .containsExactly("2년 전 오늘, 둘이 남긴 기록이 있어요");
    }

    @Test
    void 올해_기록이나_다른_날은_대상이_아니다() {
        post(LocalDate.of(2026, 11, 22));
        post(LocalDate.of(2025, 11, 21));
        fake.reset();

        assertThat(scheduler.sendMemoryToday()).isZero();

        assertThat(fake.sent()).isEmpty();
    }

    @Test
    void 설정을_끈_사람은_빠진다() {
        post(LocalDate.of(2025, 11, 22));
        userService.updateSettings(a, new UserSettingsUpdateRequest(false, null, null));
        fake.reset();

        scheduler.sendMemoryToday();

        assertThat(sentTo(tokenA)).isEmpty();
        assertThat(sentTo(tokenB)).hasSize(1);
    }

    @Test
    void 연결되지_않은_커플은_빠진다() {
        post(LocalDate.of(2025, 11, 22));
        coupleService.breakUp(a);
        fake.reset();

        assertThat(scheduler.sendMemoryToday()).isZero();

        assertThat(fake.sent()).isEmpty();
    }

    @Test
    void 스케줄러는_비동기_큐를_거치지_않고_스케줄러_스레드에서_바로_보낸다() {
        // 커플마다 @Async 로 넣으면 큐(100)가 넘쳐 버려지고 실시간 알림도 밀림
        post(LocalDate.of(2025, 11, 22));
        fake.reset();
        clearInvocations(dispatcher); // 커플 연결·기록 작성 알림(이벤트 경로)은 제외

        scheduler.sendMemoryToday();

        verify(dispatcher).sendToUsersNow(any(), any());
        verify(dispatcher, never()).sendToUsers(any(), any());
    }
}
