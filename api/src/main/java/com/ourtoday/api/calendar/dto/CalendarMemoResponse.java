package com.ourtoday.api.calendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CalendarMemoResponse(
    @Schema(description = "메모 ID") UUID id,
    @Schema(description = "표시 날짜") LocalDate date,
    @Schema(description = "제목") String title,
    @Schema(description = "추가 설명") String description,
    @Schema(description = "표시 색상") String color,
    @Schema(description = "첨부 장소 (없으면 null)") PlaceLocation location,
    @Schema(description = "작성자") Author author,
    Instant createdAt,
    Instant updatedAt
) {

    public record Author(UUID id, String nickname) {}
}
