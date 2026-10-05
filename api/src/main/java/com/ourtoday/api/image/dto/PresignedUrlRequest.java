package com.ourtoday.api.image.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PresignedUrlRequest(
    @Schema(description = "image/jpeg, image/png, image/webp, image/heic, image/heif", example = "image/jpeg")
    @NotBlank String contentType,

    @Schema(description = "파일 크기(byte). 최대 10MB. 업로드 시 이 크기와 달라지면 거부됨", example = "2048000")
    @Positive @Max(10 * 1024 * 1024) long contentLength
) {}
