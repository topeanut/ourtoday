package com.ourtoday.api.comment.dto;

import com.ourtoday.api.post.dto.PostResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
    @Schema(description = "댓글 ID") UUID id,
    @Schema(description = "작성자") PostResponse.Author author,
    @Schema(description = "내용") String content,
    @Schema(description = "작성 시각") Instant createdAt,
    @Schema(description = "수정 시각") Instant updatedAt
) {}
