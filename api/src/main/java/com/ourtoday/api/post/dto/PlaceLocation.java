package com.ourtoday.api.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 게시글에 첨부하는 장소. 좌표와 장소명은 항상 함께 있어야 한다.
 * 0.0 도 유효한 좌표라 primitive 대신 Double + @NotNull 로 누락을 구분한다.
 */
public record PlaceLocation(
    @Schema(description = "위도 (-90 ~ 90)", example = "37.5447")
    @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,

    @Schema(description = "경도 (-180 ~ 180)", example = "127.0558")
    @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,

    @Schema(description = "장소명 (최대 100자)", example = "성수동")
    @NotBlank @Size(max = 100) String placeName
) {}
