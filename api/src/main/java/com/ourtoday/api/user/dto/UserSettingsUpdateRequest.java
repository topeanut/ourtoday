package com.ourtoday.api.user.dto;

import jakarta.validation.constraints.NotNull;

public record UserSettingsUpdateRequest(
    @NotNull Boolean notifyMemoryToday,
    @NotNull Boolean notifyPartnerPost
) {}
