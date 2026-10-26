package com.ourtoday.api.bucket.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BucketItemResponse(
    @Schema(description = "항목 ID") UUID id,
    @Schema(description = "제목") String title,
    @Schema(description = "설명") String description,
    @Schema(description = "목표 날짜") LocalDate targetDate,
    @Schema(description = "완료 여부") boolean completed,
    @Schema(description = "달성 게시글 ID (게시글이 지워지면 null)") UUID completedPostId,
    @Schema(description = "완료 시각") Instant completedAt,
    @Schema(description = "추가한 사람") Author createdBy,
    Instant createdAt
) {

    public record Author(UUID id, String nickname) {}
}
