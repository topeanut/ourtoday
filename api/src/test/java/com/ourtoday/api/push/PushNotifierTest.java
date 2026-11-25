package com.ourtoday.api.push;

import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.post.PostRepository;
import com.ourtoday.api.post.PostService;
import com.ourtoday.api.post.dto.PostCreateRequest;
import com.ourtoday.api.post.dto.PostResponse;
import com.ourtoday.api.push.event.PostCreatedEvent;
import com.ourtoday.api.reaction.EmojiType;
import com.ourtoday.api.reaction.ReactionService;
import com.ourtoday.api.user.User;
import com.ourtoday.api.user.UserService;
import com.ourtoday.api.user.dto.UserSettingsUpdateRequest;
import com.ourtoday.api.user.dto.UserUpdateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 이벤트는 커밋 후에 처리되므로 @Transactional 없이 서비스를 직접 호출한다. 만든 데이터는 직접 지운다 */
@SpringBootTest
@Import(MutableClock.Config.class)
class PushNotifierTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired FakePushSender fake;
    @Autowired MutableClock clock;
    @Autowired UserService userService;
    @Autowired CoupleService coupleService;
    @Autowired PostService postService;
    @Autowired ReactionService reactionService;
    @Autowired PushTokenService tokenService;
    @Autowired PostRepository postRepository;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private final List<UUID> userIds = new ArrayList<>();
    private UUID a;
    private UUID b;
    private UUID coupleId;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        fake.reset();
        clock.reset();
        a = newUser("민지");
        b = newUser("지호");
        String code = coupleService.createInvite(a).inviteCode();
        coupleId = coupleService.join(b, code).id();
        tokenA = addToken(a);
        tokenB = addToken(b);
    }

    @AfterEach
    void cleanUp() {
        for (UUID u : userIds) {
            jdbc.update("delete from reactions where user_id = ?", u);
        }
        jdbc.update("delete from reactions where post_id in (select id from posts where couple_id = ?)", coupleId);
        jdbc.update("delete from post_images where post_id in (select id from posts where couple_id = ?)", coupleId);
        jdbc.update("delete from posts where couple_id = ?", coupleId);
        for (UUID u : userIds) {
            jdbc.update("delete from push_tokens where user_id = ?", u);
            jdbc.update("delete from couple_members where user_id = ?", u);
        }
        jdbc.update("delete from couples where id = ?", coupleId);
        jdbc.update("delete from couples where id not in (select couple_id from couple_members)");
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

    private PostResponse post(UUID author, LocalDate date) {
        return postService.create(author, new PostCreateRequest("비밀 일기 내용", date, List.of(), null));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(KST));
    }

    private List<FakePushSender.Sent> sentTo(String token) {
        return fake.sent().stream().filter(s -> s.token().equals(token)).toList();
    }

    @Test
    void 상대가_오늘_기록하면_나에게_간다() {
        PostResponse p = post(b, today());

        assertThat(sentTo(tokenA)).hasSize(1);
        PushMessage m = sentTo(tokenA).get(0).message();
        assertThat(m.body()).isEqualTo("지호가 오늘의 기록을 남겼어요");
        assertThat(m.path()).isEqualTo("/posts/" + p.id());
        assertThat(m.type()).isEqualTo("partner_post");
        assertThat(m.body()).doesNotContain("비밀 일기 내용");
        assertThat(sentTo(tokenB)).isEmpty();
    }

    @Test
    void 지난_날짜_기록은_날짜를_넣는다() {
        LocalDate past = today().minusDays(2);

        post(b, past);

        assertThat(sentTo(tokenA)).extracting(s -> s.message().body())
            .containsExactly("지호가 " + past.getMonthValue() + "월 " + past.getDayOfMonth() + "일의 기록을 남겼어요");
    }

    @Test
    void 상대_기록_알림을_끄면_안_간다() {
        userService.updateSettings(a, new UserSettingsUpdateRequest(null, false, null));

        post(b, today());

        assertThat(fake.sent()).isEmpty();
    }

    @Test
    void 반응하면_작성자에게_가고_10분_안_중복은_한_번() {
        PostResponse p = post(a, today());
        fake.reset();

        reactionService.toggle(b, p.id(), EmojiType.HEART);
        reactionService.toggle(b, p.id(), EmojiType.HEART);
        reactionService.toggle(b, p.id(), EmojiType.LAUGH);

        assertThat(fake.sent()).hasSize(1);
        FakePushSender.Sent sent = fake.sent().get(0);
        assertThat(sent.token()).isEqualTo(tokenA);
        assertThat(sent.message().body()).isEqualTo("지호가 내 기록에 반응했어요");
        assertThat(sent.message().path()).isEqualTo("/posts/" + p.id());
        assertThat(sent.message().type()).isEqualTo("reaction");

        clock.advance(Duration.ofMinutes(11));
        reactionService.toggle(b, p.id(), EmojiType.HEART);

        assertThat(fake.sent()).hasSize(2);
    }

    @Test
    void 내_글에_내가_반응하면_안_간다() {
        PostResponse p = post(a, today());
        fake.reset();

        reactionService.toggle(a, p.id(), EmojiType.HEART);

        assertThat(fake.sent()).isEmpty();
    }

    @Test
    void 반응_알림을_끄면_안_간다() {
        PostResponse p = post(a, today());
        fake.reset();
        userService.updateSettings(a, new UserSettingsUpdateRequest(null, null, false));

        reactionService.toggle(b, p.id(), EmojiType.HEART);

        assertThat(fake.sent()).isEmpty();
    }

    @Test
    void 커플이_해제되면_안_간다() {
        coupleService.breakUp(a);

        tx.executeWithoutResult(s ->
            publisher.publishEvent(new PostCreatedEvent(UUID.randomUUID(), b, coupleId, today())));

        assertThat(fake.sent()).isEmpty();
    }

    @Test
    void 합류하면_초대한_사람에게만_간다() {
        coupleService.breakUp(a);
        fake.reset();
        UUID c = newUser("하늘");
        String code = coupleService.createInvite(a).inviteCode();
        addToken(c);

        coupleService.join(c, code);

        assertThat(fake.sent()).hasSize(1);
        FakePushSender.Sent sent = fake.sent().get(0);
        assertThat(sent.token()).isEqualTo(tokenA);
        assertThat(sent.message().body()).isEqualTo("하늘님과 연결됐어요. 첫 기록을 남겨 보세요");
        assertThat(sent.message().path()).isEqualTo("/");
        assertThat(sent.message().type()).isEqualTo("couple_connected");
    }

    @Test
    void 알림이_실패해도_기록_저장은_성공한다() {
        fake.failWith(new IllegalStateException("fcm down"));

        PostResponse p = post(b, today());

        assertThat(postRepository.findById(p.id())).isPresent();
    }
}
