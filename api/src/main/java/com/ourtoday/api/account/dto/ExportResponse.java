package com.ourtoday.api.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ExportResponse(
    @Schema(description = "zip 다운로드 임시 URL") String downloadUrl,
    @Schema(description = "URL 만료 시각") Instant expiresAt
) {}
