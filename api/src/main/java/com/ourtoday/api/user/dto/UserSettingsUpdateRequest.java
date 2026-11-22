package com.ourtoday.api.user.dto;

public record UserSettingsUpdateRequest(
    Boolean notifyMemoryToday,
    Boolean notifyPartnerPost,
    Boolean notifyReaction
) {}
