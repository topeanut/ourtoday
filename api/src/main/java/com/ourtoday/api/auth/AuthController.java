package com.ourtoday.api.auth;

import com.ourtoday.api.auth.dto.TokenRefreshRequest;
import com.ourtoday.api.auth.dto.TokenResponse;
import com.ourtoday.api.common.error.BusinessException;
import com.ourtoday.api.common.error.ErrorCode;
import com.ourtoday.api.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Auth", description = "토큰 재발급")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Operation(summary = "액세스 토큰 재발급", description = "리프레시 토큰도 새로 발급(회전). 만료·위조·탈퇴 유저면 401")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        UUID userId;
        try {
            userId = tokenProvider.parseRefreshUserId(request.refreshToken());
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요.");
        }
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요.");
        }
        return new TokenResponse(tokenProvider.generateAccessToken(userId), tokenProvider.generateRefreshToken(userId));
    }
}
