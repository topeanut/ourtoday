package com.ourtoday.api.couple.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record CoupleInviteResponse(
    @Schema(description = "초대코드 (6자리)", example = "ABC234") String inviteCode,
    @Schema(description = "만료 시각 (발급 후 24시간)") Instant expiresAt
) {}
