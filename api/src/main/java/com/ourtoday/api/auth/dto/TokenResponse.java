package com.ourtoday.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
    @Schema(description = "API 호출용 (30분)") String accessToken,
    @Schema(description = "재발급용 (30일, 쓸 때마다 새로 발급)") String refreshToken
) {}
