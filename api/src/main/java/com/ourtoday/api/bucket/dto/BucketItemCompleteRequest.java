package com.ourtoday.api.bucket.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record BucketItemCompleteRequest(
    @Schema(description = "달성한 게시글 ID (선택, 우리 커플 글만)") UUID postId
) {}
