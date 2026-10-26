package com.ourtoday.api.calendar.dto;

import com.ourtoday.api.calendar.CalendarMemo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CalendarMemoCreateRequest(
    @Schema(description = "표시 날짜 (미래 가능)", example = "2026-06-14")
    @NotNull LocalDate date,

    @Schema(description = "제목 (최대 50자)", example = "1주년 기념일")
    @NotBlank @Size(max = CalendarMemo.TITLE_MAX_LENGTH) String title,

    @Schema(description = "추가 설명 (선택, 최대 1000자)", example = "OO 호텔 예약")
    @Size(max = CalendarMemo.DESCRIPTION_MAX_LENGTH) String description,

    @Schema(description = "표시 색상 #RRGGBB (선택)", example = "#FFB6C1")
    @Pattern(regexp = CalendarMemoCreateRequest.COLOR_PATTERN) String color
) {
    public static final String COLOR_PATTERN = "^#[0-9A-Fa-f]{6}$";
}
