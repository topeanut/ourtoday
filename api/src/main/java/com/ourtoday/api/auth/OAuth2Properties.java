package com.ourtoday.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param successRedirectUri 로그인 성공 후 이동할 프론트 주소. 비어 있으면 JSON 응답 (프론트 없는 개발 단계용)
 */
@ConfigurationProperties(prefix = "app.oauth2")
public record OAuth2Properties(
    String successRedirectUri
) {}
