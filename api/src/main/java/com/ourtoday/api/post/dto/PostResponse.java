package com.ourtoday.api.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PostResponse(
    @Schema(description = "게시글 ID") UUID id,
    @Schema(description = "작성자") Author author,
    @Schema(description = "본문") String content,
    @Schema(description = "기록 날짜") LocalDate postedDate,
    @Schema(description = "작성 시각") Instant createdAt,
    @Schema(description = "수정 시각") Instant updatedAt
) {

    public record Author(
        UUID id,
        String nickname,
        String profileImageUrl
    ) {}
}
