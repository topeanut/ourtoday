package com.ourtoday.api.auth.apple;

import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * .p8 키로 client_secret 을 만들어 Apple 서버와 통신.
 *  - 로그인 때 인가 코드 → refresh token 교환 (탈퇴 때 철회하려고 보관)
 *  - 탈퇴 때 refresh token 철회 (App Store 심사 지침 5.1.1(v))
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppleAuthClient {

    private static final String BASE_URL = "https://appleid.apple.com/auth";
    private static final Duration SECRET_TTL = Duration.ofMinutes(5);

    private final AppleProperties properties;
    private final Clock clock;
    private final RestClient restClient = RestClient.create();

    /** @return Apple refresh token. 키 미설정·코드 없음·교환 실패면 null (로그인은 계속 진행) */
    public String exchangeCode(String authorizationCode, String clientId) {
        if (!properties.hasSigningKey() || authorizationCode == null || authorizationCode.isBlank()) {
            return null;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret(clientId));
        form.add("code", authorizationCode);
        form.add("grant_type", "authorization_code");
        // 웹(Services ID) 코드는 발급 때 쓴 redirect_uri 를 같이 보내야 함
        if (clientId.equals(properties.webClientId())) {
            form.add("redirect_uri", properties.webRedirectUri());
        }
        try {
            JsonNode body = restClient.post().uri(BASE_URL + "/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
            JsonNode refresh = body == null ? null : body.get("refresh_token");
            return refresh == null ? null : refresh.asText();
        } catch (RuntimeException e) {
            log.warn("Apple 인가 코드 교환 실패 (clientId={}): {}", clientId, e.getMessage());
            return null;
        }
    }

    /** 실패해도 예외를 던지지 않음 (탈퇴는 이미 끝난 뒤라 로그만) */
    public void revoke(String refreshToken, String clientId) {
        if (!properties.hasSigningKey() || refreshToken == null || clientId == null) return;
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret(clientId));
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");
        try {
            restClient.post().uri(BASE_URL + "/revoke")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("Apple 토큰 철회 실패 (clientId={}): {}", clientId, e.getMessage());
        }
    }

    String clientSecret(String clientId) {
        Instant now = clock.instant();
        return Jwts.builder()
            .header().keyId(properties.keyId()).and()
            .issuer(properties.teamId())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(SECRET_TTL)))
            .audience().add(AppleJwksIdentityVerifier.ISSUER).and()
            .subject(clientId)
            .signWith(privateKey(), Jwts.SIG.ES256)
            .compact();
    }

    private PrivateKey privateKey() {
        String pem = properties.privateKey()
            .replace("\\n", "\n")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        } catch (Exception e) {
            throw new IllegalStateException("APPLE_PRIVATE_KEY 형식이 올바르지 않습니다.", e);
        }
    }
}
