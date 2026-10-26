package com.ourtoday.api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private SecretKey key;

    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId) {
        return generate(userId, TYPE_ACCESS, properties.accessTokenValidityMs());
    }

    /** 액세스 토큰 재발급 전용 (API 호출에는 못 씀) */
    public String generateRefreshToken(UUID userId) {
        return generate(userId, TYPE_REFRESH, properties.refreshTokenValidityMs());
    }

    /** 액세스 토큰 → userId. 리프레시 토큰이면 거부 (typ 없는 기존 토큰은 액세스로 취급) */
    public UUID parseUserId(String token) {
        Claims claims = parse(token);
        if (TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("refresh token 은 API 호출에 사용할 수 없음");
        }
        return UUID.fromString(claims.getSubject());
    }

    public UUID parseRefreshUserId(String token) {
        Claims claims = parse(token);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("refresh token 아님");
        }
        return UUID.fromString(claims.getSubject());
    }

    private String generate(UUID userId, String type, long validityMs) {
        Date now = new Date();
        return Jwts.builder()
            .subject(userId.toString())
            .claim(CLAIM_TYPE, type)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + validityMs))
            .signWith(key)
            .compact();
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
