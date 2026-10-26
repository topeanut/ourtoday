package com.ourtoday.api.bucket.dto;

import com.ourtoday.api.bucket.BucketItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BucketItemCreateRequest(
    @Schema(description = "제목 (최대 100자)", example = "오로라 보러 가기")
    @NotBlank @Size(max = BucketItem.TITLE_MAX_LENGTH) String title,

    @Schema(description = "설명 (선택, 최대 1000자)", example = "아이슬란드")
    @Size(max = BucketItem.DESCRIPTION_MAX_LENGTH) String description,

    @Schema(description = "목표 날짜 (선택)", example = "2027-01-01")
    LocalDate targetDate
) {}
