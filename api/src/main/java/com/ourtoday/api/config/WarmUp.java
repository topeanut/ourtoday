package com.ourtoday.api.config;

import com.ourtoday.api.auth.JwtTokenProvider;
import com.ourtoday.api.couple.CoupleMember;
import com.ourtoday.api.couple.CoupleMemberRepository;
import com.ourtoday.api.couple.CoupleRepository;
import com.ourtoday.api.couple.CoupleStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 배포 직후 첫 사용자가 느리지 않게 서버가 자기 자신에게 실제 요청을 몇 번 보내 데워 둠.
 * <p>
 * 막 뜬 JVM 은 클래스 로딩·JIT·Hibernate 쿼리 준비가 첫 요청 때 일어나서, 1GB 서버에서는
 * 앱 첫 화면 요청들이 3~6초씩 걸렸음 (데워진 뒤엔 0.1~0.5초).
 * 읽기 전용 API + 상태 없는 토큰 재발급만 호출하므로 데이터는 바뀌지 않음. 실패해도 무시
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class WarmUp {

    static final int ROUNDS = 3;
    static final List<String> GET_PATHS = List.of(
        "/users/me",
        "/users/me/onboarding-status",
        "/couples/me",
        "/posts?page=0&size=10",
        "/posts/memories/today"
    );

    private final CoupleRepository coupleRepository;
    private final CoupleMemberRepository coupleMemberRepository;
    private final JwtTokenProvider tokenProvider;
    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        // 요청 처리는 이미 시작됐으므로 별도 스레드에서 (기동을 늦추지 않게)
        Thread.ofVirtual().name("warm-up").start(this::run);
    }

    /** @return 성공한 요청 수 (테스트용) */
    int run() {
        long started = System.currentTimeMillis();
        int ok = 0;
        try {
            UUID userId = anyConnectedUser();
            if (userId == null) {
                log.info("워밍업 건너뜀: 연결된 커플 없음");
                return 0;
            }
            String port = environment.getProperty("local.server.port", "8080");
            RestClient client = RestClient.builder().baseUrl("http://localhost:" + port + "/api/v1").build();
            String access = "Bearer " + tokenProvider.generateAccessToken(userId);
            String refresh = tokenProvider.generateRefreshToken(userId);

            for (int round = 0; round < ROUNDS; round++) {
                for (String path : GET_PATHS) {
                    ok += call(() -> client.get().uri(path).header("Authorization", access).retrieve().toBodilessEntity());
                }
                ok += call(() -> client.post().uri("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("refreshToken", refresh)).retrieve().toBodilessEntity());
            }
            log.info("워밍업 완료 {}ms (성공 {}/{})", System.currentTimeMillis() - started, ok, ROUNDS * (GET_PATHS.size() + 1));
        } catch (RuntimeException e) {
            log.warn("워밍업 실패 (무시): {}", e.toString());
        }
        return ok;
    }

    private UUID anyConnectedUser() {
        return coupleRepository.findByStatus(CoupleStatus.CONNECTED).stream()
            .findFirst()
            .flatMap(c -> coupleMemberRepository.findByCoupleIdOrderByJoinedAtAsc(c.getId()).stream().findFirst())
            .map(CoupleMember::getUserId)
            .orElse(null);
    }

    private static int call(Runnable request) {
        try {
            request.run();
            return 1;
        } catch (RuntimeException e) {
            // 개별 요청 실패는 워밍업 목적상 상관없음
            return 0;
        }
    }
}
