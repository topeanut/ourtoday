package com.ourtoday.api.bucket.dto;

import com.ourtoday.api.bucket.BucketItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 전체 교체 (description·targetDate 를 null 로 보내면 지움) */
public record BucketItemUpdateRequest(
    @NotBlank @Size(max = BucketItem.TITLE_MAX_LENGTH) String title,
    @Size(max = BucketItem.DESCRIPTION_MAX_LENGTH) String description,
    LocalDate targetDate
) {}
