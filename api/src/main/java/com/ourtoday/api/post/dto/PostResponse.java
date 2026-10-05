package com.ourtoday.api.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PostResponse(
    @Schema(description = "게시글 ID") UUID id,
    @Schema(description = "작성자") Author author,
    @Schema(description = "본문") String content,
    @Schema(description = "기록 날짜") LocalDate postedDate,
    @Schema(description = "이미지 (표시 순서)") List<Image> images,
    @Schema(description = "작성 시각") Instant createdAt,
    @Schema(description = "수정 시각") Instant updatedAt
) {

    public record Author(
        UUID id,
        String nickname,
        String profileImageUrl
    ) {}

    public record Image(
        @Schema(description = "임시 조회 URL (1시간 유효)") String url,
        @Schema(description = "수정 시 imageKeys 에 다시 넣을 값") String imageKey,
        int order
    ) {}
}
