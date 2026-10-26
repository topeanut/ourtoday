package com.ourtoday.api.calendar;

import com.ourtoday.api.calendar.dto.CalendarMemoCreateRequest;
import com.ourtoday.api.calendar.dto.CalendarMemoListResponse;
import com.ourtoday.api.calendar.dto.CalendarMemoResponse;
import com.ourtoday.api.calendar.dto.CalendarMemoUpdateRequest;
import com.ourtoday.api.common.guard.RequireCouple;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Calendar", description = "캘린더 메모/일정")
@RestController
@RequestMapping("/api/v1/calendar/memos")
@RequiredArgsConstructor
@RequireCouple
public class CalendarMemoController {

    private final CalendarMemoService calendarMemoService;

    @Operation(summary = "메모 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarMemoResponse create(@AuthenticationPrincipal UUID userId,
                                       @Valid @RequestBody CalendarMemoCreateRequest request) {
        return calendarMemoService.create(userId, request);
    }

    @Operation(summary = "기간 조회 (최대 100일, 날짜·작성 순)")
    @GetMapping
    public CalendarMemoListResponse list(
        @AuthenticationPrincipal UUID userId,
        @Parameter(description = "시작일 (YYYY-MM-DD)")
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @Parameter(description = "종료일 (YYYY-MM-DD, 포함)")
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return calendarMemoService.list(userId, from, to);
    }

    @Operation(summary = "상세")
    @GetMapping("/{id}")
    public CalendarMemoResponse get(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return calendarMemoService.get(userId, id);
    }

    @Operation(summary = "수정 (작성자만)")
    @PatchMapping("/{id}")
    public CalendarMemoResponse update(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                                       @Valid @RequestBody CalendarMemoUpdateRequest request) {
        return calendarMemoService.update(userId, id, request);
    }

    @Operation(summary = "삭제 (작성자만)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        calendarMemoService.delete(userId, id);
    }
}
