package com.ourtoday.api.calendar.dto;

import com.ourtoday.api.calendar.CalendarMemo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 전체 교체 (description·color 를 null 로 보내면 지움) */
public record CalendarMemoUpdateRequest(
    @NotNull LocalDate date,
    @NotBlank @Size(max = CalendarMemo.TITLE_MAX_LENGTH) String title,
    @Size(max = CalendarMemo.DESCRIPTION_MAX_LENGTH) String description,
    @Schema(description = "#RRGGBB") @Pattern(regexp = CalendarMemoCreateRequest.COLOR_PATTERN) String color
) {}
