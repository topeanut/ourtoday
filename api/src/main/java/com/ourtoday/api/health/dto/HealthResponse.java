package com.ourtoday.api.health.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record HealthResponse(
    @Schema(description = "서비스 상태", example = "OK") String status,
    @Schema(description = "응답 시각 (ISO-8601)", example = "2026-05-25T10:23:45Z") String timestamp
) {}
