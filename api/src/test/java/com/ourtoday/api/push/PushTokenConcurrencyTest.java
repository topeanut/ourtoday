package com.ourtoday.api.push;

import com.ourtoday.api.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 앱이 처음 권한을 받을 때 같은 토큰으로 등록 요청이 거의 동시에 두 번 오는 경우.
 * 실제 커밋이 필요해 @Transactional 을 쓰지 않고, 만든 행은 직접 지운다
 */
@SpringBootTest
class PushTokenConcurrencyTest {

    private static final int ROUNDS = 20;

    @Autowired PushTokenService service;
    @Autowired UserService userService;
    @Autowired JdbcTemplate jdbc;

    private final List<UUID> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID u : userIds) {
            jdbc.update("delete from push_tokens where user_id = ?", u);
            jdbc.update("delete from user_settings where user_id = ?", u);
            jdbc.update("delete from users where id = ?", u);
        }
        userIds.clear();
    }

    private UUID newUser() {
        UUID id = userService.findOrCreateByKakaoId("kakao-" + UUID.randomUUID()).getId();
        userIds.add(id);
        return id;
    }

    @Test
    void 같은_토큰을_동시에_등록해도_예외_없이_한_건만_남는다() throws Exception {
        UUID a = newUser();
        UUID b = newUser();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < ROUNDS; i++) {
                String token = "race-" + UUID.randomUUID();
                CountDownLatch start = new CountDownLatch(1);
                Future<?> f1 = pool.submit(() -> {
                    start.await();
                    service.register(a, token, PushPlatform.ANDROID);
                    return null;
                });
                Future<?> f2 = pool.submit(() -> {
                    start.await();
                    service.register(b, token, PushPlatform.IOS);
                    return null;
                });
                start.countDown();

                // 둘 다 예외 없이 끝나야 함 (예외면 get() 이 ExecutionException)
                f1.get(10, TimeUnit.SECONDS);
                f2.get(10, TimeUnit.SECONDS);

                List<UUID> owners = jdbc.queryForList(
                    "select user_id from push_tokens where token = ?", UUID.class, token);
                assertThat(owners).hasSize(1);
                // 먼저/나중 순서는 정해지지 않으므로 둘 중 한 계정이면 됨 (계정·플랫폼은 같은 요청 값으로 짝지어짐)
                assertThat(owners.get(0)).isIn(a, b);
                String platform = jdbc.queryForObject(
                    "select platform from push_tokens where token = ?", String.class, token);
                assertThat(platform).isEqualTo(owners.get(0).equals(a) ? "ANDROID" : "IOS");
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
