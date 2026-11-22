package com.ourtoday.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record KakaoPrepareResponse(
    @Schema(description = "Kakao.Auth.authorize 의 state 로 그대로 넘김") String state,
    @Schema(description = "Kakao.Auth.authorize 의 redirectUri 로 그대로 넘김") String redirectUri
) {}
