package com.ourtoday.api.auth.apple;

/**
 * 검증된 Apple identity token 에서 꺼낸 값
 *
 * @param subject  Apple 회원 식별값 (팀 안에서 고정)
 * @param clientId 토큰을 발급받은 클라이언트 (iOS Bundle ID 또는 웹 Services ID)
 */
public record AppleIdentity(String subject, String clientId) {}
