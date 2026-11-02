package com.ourtoday.api.auth.apple;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.security.Key;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Apple 공개키(JWKS)로 identity token 서명·발급자·대상·만료를 확인.
 * 공개키는 1시간 캐시하고, 모르는 kid 가 오면(Apple 키 교체) 한 번 다시 받음
 */
@Component
@RequiredArgsConstructor
public class AppleJwksIdentityVerifier implements AppleIdentityVerifier {

    static final String ISSUER = "https://appleid.apple.com";
    private static final String JWKS_URL = "https://appleid.apple.com/auth/keys";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final AppleProperties properties;
    private final Clock clock;
    private final RestClient restClient = RestClient.create();

    private volatile Map<String, Key> keys = Map.of();
    private volatile Instant fetchedAt = Instant.EPOCH;

    @Override
    public AppleIdentity verify(String identityToken) {
        Claims claims = Jwts.parser()
            .keyLocator(new LocatorAdapter<Key>() {
                @Override
                protected Key locate(JwsHeader header) {
                    return findKey(header.getKeyId());
                }
            })
            .requireIssuer(ISSUER)
            .clock(() -> Date.from(clock.instant()))
            .build()
            .parseSignedClaims(identityToken)
            .getPayload();

        String clientId = claims.getAudience().stream()
            .filter(properties.clientIds()::contains)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("허용하지 않은 aud: " + claims.getAudience()));
        if (!StringUtils.hasText(claims.getSubject())) {
            throw new IllegalArgumentException("sub 없음");
        }
        return new AppleIdentity(claims.getSubject(), clientId);
    }

    private Key findKey(String kid) {
        if (kid == null) throw new IllegalArgumentException("kid 없음");
        Key key = cachedKeys().get(kid);
        if (key == null) {
            key = refresh().get(kid);
        }
        if (key == null) throw new IllegalArgumentException("모르는 kid: " + kid);
        return key;
    }

    private Map<String, Key> cachedKeys() {
        if (fetchedAt.plus(CACHE_TTL).isBefore(clock.instant())) {
            return refresh();
        }
        return keys;
    }

    /** 테스트용: 네트워크 없이 공개키 캐시 채우기 */
    void preload(Map<String, Key> preloaded) {
        keys = Map.copyOf(preloaded);
        fetchedAt = clock.instant();
    }

    private synchronized Map<String, Key> refresh() {
        String json = restClient.get().uri(JWKS_URL).retrieve().body(String.class);
        JwkSet set = Jwks.setParser().build().parse(json);
        Set<Jwk<?>> jwks = set.getKeys();
        keys = jwks.stream().collect(Collectors.toUnmodifiableMap(Jwk::getId, jwk -> (Key) jwk.toKey()));
        fetchedAt = clock.instant();
        return keys;
    }
}
