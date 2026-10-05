package com.ourtoday.api.image.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PresignedUrlResponse(
    @Schema(description = "PUT 업로드 URL. 요청과 같은 Content-Type·Content-Length 헤더로 보내야 함") String uploadUrl,
    @Schema(description = "게시글 작성/수정 시 imageKeys 에 넣을 값") String imageKey,
    @Schema(description = "uploadUrl 유효시간(초)") long expiresInSec
) {}
