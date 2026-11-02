package com.ourtoday.api.auth.apple;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 네트워크 없이: Apple 공개키 캐시를 미리 채워서 서명·발급자·대상·만료 검증 확인 */
class AppleJwksIdentityVerifierTest {

    static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    final KeyPair keyPair = Jwts.SIG.RS256.keyPair().build();
    final AppleProperties props = new AppleProperties("T", null, null, List.of("app.ourtoday", "app.ourtoday.web"), "app.ourtoday.web", "https://x/cb");
    final AppleJwksIdentityVerifier verifier = verifierWithKey("kid-1", keyPair);

    @Test
    void 올바른_토큰이면_sub_와_clientId_를_돌려준다() {
        AppleIdentity identity = verifier.verify(token("kid-1", AppleJwksIdentityVerifier.ISSUER, "app.ourtoday.web", NOW.plusSeconds(600)));
        assertThat(identity).isEqualTo(new AppleIdentity("sub-1", "app.ourtoday.web"));
    }

    @Test
    void 다른_앱용_토큰_발급자_다름_만료는_거부한다() {
        assertThatThrownBy(() -> verifier.verify(token("kid-1", AppleJwksIdentityVerifier.ISSUER, "com.other.app", NOW.plusSeconds(600))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> verifier.verify(token("kid-1", "https://evil.example", "app.ourtoday", NOW.plusSeconds(600))))
            .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> verifier.verify(token("kid-1", AppleJwksIdentityVerifier.ISSUER, "app.ourtoday", NOW.minusSeconds(600))))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void 다른_키로_서명한_토큰은_거부한다() {
        KeyPair other = Jwts.SIG.RS256.keyPair().build();
        String forged = Jwts.builder().header().keyId("kid-1").and()
            .issuer(AppleJwksIdentityVerifier.ISSUER).audience().add("app.ourtoday").and().subject("sub-1")
            .expiration(Date.from(NOW.plusSeconds(600))).signWith(other.getPrivate()).compact();
        assertThatThrownBy(() -> verifier.verify(forged)).isInstanceOf(RuntimeException.class);
    }

    private String token(String kid, String iss, String aud, Instant exp) {
        return Jwts.builder().header().keyId(kid).and()
            .issuer(iss).audience().add(aud).and().subject("sub-1")
            .issuedAt(Date.from(NOW.minusSeconds(60))).expiration(Date.from(exp))
            .signWith(keyPair.getPrivate()).compact();
    }

    private AppleJwksIdentityVerifier verifierWithKey(String kid, KeyPair pair) {
        AppleJwksIdentityVerifier v = new AppleJwksIdentityVerifier(props, Clock.fixed(NOW, ZoneOffset.UTC));
        v.preload(Map.of(kid, pair.getPublic()));
        return v;
    }
}
