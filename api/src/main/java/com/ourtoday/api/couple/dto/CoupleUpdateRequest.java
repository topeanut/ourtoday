package com.ourtoday.api.couple.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CoupleUpdateRequest(
    @Schema(description = "사귄 날짜 (오늘 이후 불가)", example = "2024-03-14")
    @NotNull LocalDate startedAt
) {}
