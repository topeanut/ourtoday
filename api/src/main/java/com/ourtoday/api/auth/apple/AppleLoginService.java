package com.ourtoday.api.auth.apple;

import com.ourtoday.api.auth.JwtTokenProvider;
import com.ourtoday.api.auth.dto.AppleLoginRequest;
import com.ourtoday.api.auth.dto.TokenResponse;
import com.ourtoday.api.common.error.BusinessException;
import com.ourtoday.api.common.error.ErrorCode;
import com.ourtoday.api.user.User;
import com.ourtoday.api.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Apple 로그인: iOS 앱(네이티브)·웹(Apple JS 팝업) 모두 identity token 을 보내면 우리 JWT 를 발급.
 * Apple 서버 통신은 트랜잭션 밖에서 (DB 연결을 오래 잡지 않게)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppleLoginService {

    private final AppleIdentityVerifier verifier;
    private final AppleAuthClient appleAuthClient;
    private final UserService userService;
    private final JwtTokenProvider tokenProvider;

    public TokenResponse login(AppleLoginRequest request) {
        AppleIdentity identity;
        try {
            identity = verifier.verify(request.identityToken());
        } catch (RuntimeException e) {
            log.info("Apple identity token 검증 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Apple 로그인을 확인하지 못했어요. 다시 시도해 주세요.");
        }
        String refreshToken = appleAuthClient.exchangeCode(request.authorizationCode(), identity.clientId());
        User user = userService.findOrCreateByApple(identity.subject(), refreshToken, identity.clientId());
        return new TokenResponse(tokenProvider.generateAccessToken(user.getId()), tokenProvider.generateRefreshToken(user.getId()));
    }
}
