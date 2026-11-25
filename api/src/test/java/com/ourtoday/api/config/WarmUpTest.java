package com.ourtoday.api.config;

import com.ourtoday.api.auth.JwtTokenProvider;
import com.ourtoday.api.couple.CoupleMemberRepository;
import com.ourtoday.api.couple.CoupleRepository;
import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.image.TestStorageConfig;
import com.ourtoday.api.user.UserService;
import com.ourtoday.api.user.dto.UserUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 포트로 띄워서 워밍업 요청이 전부 성공(2xx)하는지 확인. 운영 전용 빈이라 직접 생성.
 * 실제 HTTP 요청이라 롤백이 안 되므로 별도 메모리 DB 사용 (테이블이 비어 있다고 가정하는 다른 테스트 보호)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
    "spring.datasource.url=jdbc:h2:mem:warmup;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@Import(TestStorageConfig.class)
class WarmUpTest {

    @Autowired CoupleRepository coupleRepository;
    @Autowired CoupleMemberRepository coupleMemberRepository;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired Environment environment;
    @Autowired UserService userService;
    @Autowired CoupleService coupleService;

    @Test
    void 연결된_커플_사용자로_조회_API와_토큰_재발급을_모두_성공() {
        UUID a = newUser("워밍A");
        UUID b = newUser("워밍B");
        coupleService.join(b, coupleService.createInvite(a).inviteCode());

        WarmUp warmUp = new WarmUp(coupleRepository, coupleMemberRepository, tokenProvider, environment);

        assertThat(warmUp.run()).isEqualTo(WarmUp.ROUNDS * (WarmUp.GET_PATHS.size() + 1));
    }

    private UUID newUser(String nickname) {
        UUID id = userService.findOrCreateByKakaoId("kakao-" + UUID.randomUUID()).getId();
        userService.updateMe(id, new UserUpdateRequest(nickname, null));
        return id;
    }
}
