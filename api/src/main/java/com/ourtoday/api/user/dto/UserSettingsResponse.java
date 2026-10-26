package com.ourtoday.api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserSettingsResponse(
    @Schema(description = "작년 오늘 알림") boolean notifyMemoryToday,
    @Schema(description = "상대가 글/메모 작성 시 알림") boolean notifyPartnerPost
) {}
