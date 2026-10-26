package com.ourtoday.api.dev;

import com.ourtoday.api.auth.JwtTokenProvider;
import com.ourtoday.api.auth.dto.TokenResponse;
import com.ourtoday.api.dev.dto.DevLoginRequest;
import com.ourtoday.api.user.User;
import com.ourtoday.api.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개발 서버 전용 임시 로그인 (카카오 없이 이름만으로). dev 프로필에서만 빈이 생겨 운영에는 엔드포인트 자체가 없음
 */
@Tag(name = "Dev", description = "개발 서버 전용")
@Profile("dev")
@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
public class DevLoginController {

    private final UserService userService;
    private final JwtTokenProvider tokenProvider;

    @Operation(summary = "임시 로그인", description = "이름별로 계정을 만들거나 재사용. 이후 흐름(프로필·커플 연결)은 실제와 같음")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody DevLoginRequest request) {
        User user = userService.findOrCreateByKakaoId("dev:" + request.name());
        return new TokenResponse(tokenProvider.generateAccessToken(user.getId()),
            tokenProvider.generateRefreshToken(user.getId()));
    }
}
