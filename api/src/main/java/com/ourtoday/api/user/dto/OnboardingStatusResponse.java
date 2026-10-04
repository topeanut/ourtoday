package com.ourtoday.api.user.dto;

import com.ourtoday.api.user.OnboardingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record OnboardingStatusResponse(
    @Schema(description = "온보딩 단계") OnboardingStatus status
) {}
