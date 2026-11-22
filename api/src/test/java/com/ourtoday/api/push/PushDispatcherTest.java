package com.ourtoday.api.push;

import com.ourtoday.api.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@Transactional
class PushDispatcherTest {

    @Autowired PushDispatcher dispatcher;
    @Autowired PushTokenRepository repository;
    @Autowired FakePushSender fake;
    @Autowired UserService userService;

    private final PushMessage message = new PushMessage("새 댓글이 달렸어요", "/posts/1", "COMMENT");

    @BeforeEach
    void setUp() {
        fake.reset();
    }

    private UUID newUser() {
        return userService.findOrCreateByKakaoId("kakao-" + UUID.randomUUID()).getId();
    }

    private String addToken(UUID userId) {
        String token = "tok-" + UUID.randomUUID();
        repository.save(PushToken.create(userId, token, PushPlatform.ANDROID));
        return token;
    }

    @Test
    void 사용자의_모든_기기로_보낸다() {
        UUID a = newUser();
        String t1 = addToken(a);
        String t2 = addToken(a);
        addToken(newUser());

        dispatcher.sendToUsers(List.of(a), message);

        assertThat(fake.sent()).extracting(FakePushSender.Sent::token).containsExactlyInAnyOrder(t1, t2);
        assertThat(fake.sent()).allSatisfy(s -> assertThat(s.message()).isEqualTo(message));
    }

    @Test
    void 무효_토큰은_삭제하고_나머지는_보낸다() {
        UUID a = newUser();
        String t1 = addToken(a);
        String t2 = addToken(a);
        fake.markInvalid(t1);

        dispatcher.sendToUsers(List.of(a), message);

        assertThat(repository.findByToken(t1)).isEmpty();
        assertThat(repository.findByToken(t2)).isPresent();
        assertThat(fake.sent()).extracting(FakePushSender.Sent::token).contains(t2);
    }

    @Test
    void 발송기가_예외를_던져도_밖으로_새지_않는다() {
        UUID a = newUser();
        addToken(a);
        fake.failWith(new RuntimeException("boom"));

        assertThatCode(() -> dispatcher.sendToUsers(List.of(a), message)).doesNotThrowAnyException();
    }

    @Test
    void 서비스계정이_없으면_Noop_발송기가_만들어진다() {
        assertThat(new PushConfig().pushSender("")).isInstanceOf(NoopPushSender.class);
        assertThat(new PushConfig().pushSender("   ")).isInstanceOf(NoopPushSender.class);
    }
}
