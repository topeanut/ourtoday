package com.ourtoday.api.calendar;

import com.ourtoday.api.calendar.dto.CalendarMemoCreateRequest;
import com.ourtoday.api.calendar.dto.CalendarMemoListResponse;
import com.ourtoday.api.calendar.dto.CalendarMemoResponse;
import com.ourtoday.api.calendar.dto.CalendarMemoUpdateRequest;
import com.ourtoday.api.common.error.BusinessException;
import com.ourtoday.api.common.error.ErrorCode;
import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.user.User;
import com.ourtoday.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarMemoService {

    /** 한 번에 조회할 수 있는 최대 기간 (월간 뷰 + 앞뒤 여유) */
    static final long MAX_RANGE_DAYS = 100;

    private final CalendarMemoRepository calendarMemoRepository;
    private final UserRepository userRepository;
    private final CoupleService coupleService;

    @Transactional
    public CalendarMemoResponse create(UUID userId, CalendarMemoCreateRequest request) {
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        CalendarMemo memo = calendarMemoRepository.save(CalendarMemo.create(coupleId, userId, request.date(),
            request.title().strip(), blankToNull(request.description()), normalizeColor(request.color())));
        return toResponse(memo, loadUsers(List.of(userId)));
    }

    @Transactional(readOnly = true)
    public CalendarMemoListResponse list(UUID userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "from 은 to 보다 늦을 수 없습니다.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "조회 기간은 최대 " + MAX_RANGE_DAYS + "일입니다.");
        }
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        List<CalendarMemo> memos =
            calendarMemoRepository.findByCoupleIdAndDateBetweenOrderByDateAscCreatedAtAsc(coupleId, from, to);
        Map<UUID, User> users = loadUsers(memos.stream().map(CalendarMemo::getAuthorId).collect(Collectors.toSet()));
        return new CalendarMemoListResponse(memos.stream().map(m -> toResponse(m, users)).toList());
    }

    @Transactional(readOnly = true)
    public CalendarMemoResponse get(UUID userId, UUID memoId) {
        CalendarMemo memo = getMemoInMyCouple(userId, memoId);
        return toResponse(memo, loadUsers(List.of(memo.getAuthorId())));
    }

    @Transactional
    public CalendarMemoResponse update(UUID userId, UUID memoId, CalendarMemoUpdateRequest request) {
        CalendarMemo memo = getMyMemo(userId, memoId);
        memo.update(request.date(), request.title().strip(), blankToNull(request.description()),
            normalizeColor(request.color()));
        return toResponse(memo, loadUsers(List.of(userId)));
    }

    @Transactional
    public void delete(UUID userId, UUID memoId) {
        calendarMemoRepository.delete(getMyMemo(userId, memoId));
    }

    private CalendarMemo getMemoInMyCouple(UUID userId, UUID memoId) {
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        CalendarMemo memo = calendarMemoRepository.findById(memoId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "메모를 찾을 수 없습니다."));
        if (!memo.getCoupleId().equals(coupleId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "우리 커플의 메모가 아닙니다.");
        }
        return memo;
    }

    private CalendarMemo getMyMemo(UUID userId, UUID memoId) {
        CalendarMemo memo = getMemoInMyCouple(userId, memoId);
        if (!memo.isWrittenBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "작성자만 수정/삭제할 수 있습니다.");
        }
        return memo;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private static String normalizeColor(String color) {
        return StringUtils.hasText(color) ? color.toUpperCase() : null;
    }

    private Map<UUID, User> loadUsers(Collection<UUID> ids) {
        return userRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private CalendarMemoResponse toResponse(CalendarMemo memo, Map<UUID, User> users) {
        User author = users.get(memo.getAuthorId());
        return new CalendarMemoResponse(
            memo.getId(),
            memo.getDate(),
            memo.getTitle(),
            memo.getDescription(),
            memo.getColor(),
            new CalendarMemoResponse.Author(author.getId(), author.getNickname()),
            memo.getCreatedAt(),
            memo.getUpdatedAt()
        );
    }
}
