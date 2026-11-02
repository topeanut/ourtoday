package com.ourtoday.api.auth.apple;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Sign in with Apple 설정
 *
 * @param teamId         Apple Developer 팀 ID
 * @param keyId          Sign in with Apple 키(.p8)의 Key ID
 * @param privateKey     .p8 파일 내용(PEM). 환경변수에서는 줄바꿈을 \n 으로 써도 됨
 * @param clientIds      identity token 의 aud 로 허용할 값. iOS 앱 Bundle ID + 웹 Services ID
 * @param webClientId    웹 로그인용 Services ID (인가 코드 교환 시 redirect_uri 필요 여부 판단)
 * @param webRedirectUri 웹 로그인 반환 URL (Apple Developer 에 등록한 값과 같아야 함)
 */
@ConfigurationProperties(prefix = "app.apple")
public record AppleProperties(
    String teamId,
    String keyId,
    String privateKey,
    List<String> clientIds,
    String webClientId,
    String webRedirectUri
) {

    public AppleProperties {
        clientIds = clientIds == null ? List.of() : List.copyOf(clientIds);
    }

    /** .p8 키까지 있어야 인가 코드 교환·토큰 철회 가능. 없으면 로그인만 동작 */
    public boolean hasSigningKey() {
        return StringUtils.hasText(teamId) && StringUtils.hasText(keyId) && StringUtils.hasText(privateKey);
    }
}
