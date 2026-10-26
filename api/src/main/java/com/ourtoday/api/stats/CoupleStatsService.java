package com.ourtoday.api.stats;

import com.ourtoday.api.calendar.CalendarMemoRepository;
import com.ourtoday.api.common.error.BusinessException;
import com.ourtoday.api.common.error.ErrorCode;
import com.ourtoday.api.couple.Couple;
import com.ourtoday.api.couple.CoupleRepository;
import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.post.PostRepository;
import com.ourtoday.api.reaction.ReactionRepository;
import com.ourtoday.api.stats.dto.CoupleStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CoupleStatsService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final CoupleService coupleService;
    private final CoupleRepository coupleRepository;
    private final PostRepository postRepository;
    private final CalendarMemoRepository calendarMemoRepository;
    private final ReactionRepository reactionRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CoupleStatsResponse getStats(UUID userId) {
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        UUID partnerId = coupleService.getPartnerId(coupleId, userId);
        Couple couple = coupleRepository.findById(coupleId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_REQUIRED));
        LocalDate today = LocalDate.now(clock.withZone(SERVICE_ZONE));

        return new CoupleStatsResponse(
            memberStats(coupleId, userId, today),
            memberStats(coupleId, partnerId, today),
            new CoupleStatsResponse.CoupleSummary(
                ChronoUnit.DAYS.between(couple.getStartedAt(), today) + 1,
                postRepository.findFirstPostedDate(coupleId).orElse(null))
        );
    }

    private CoupleStatsResponse.MemberStats memberStats(UUID coupleId, UUID memberId, LocalDate today) {
        YearMonth month = YearMonth.from(today);
        return new CoupleStatsResponse.MemberStats(
            postRepository.countByCoupleIdAndAuthorId(coupleId, memberId),
            postRepository.countByCoupleIdAndAuthorIdAndPostedDateBetween(
                coupleId, memberId, month.atDay(1), month.atEndOfMonth()),
            consecutiveDays(postRepository.findPostedDatesDesc(coupleId, memberId, today), today),
            calendarMemoRepository.countByCoupleIdAndAuthorId(coupleId, memberId),
            reactionRepository.countGivenInCouple(coupleId, memberId)
        );
    }

    /**
     * 오늘부터 거꾸로 끊기지 않은 작성일 수. 오늘 아직 안 썼으면 어제부터 셈 (하루 중간에 0 으로 떨어지지 않게)
     *
     * @param datesDesc 오늘 이전 작성 날짜, 최신순·중복 없음
     */
    static int consecutiveDays(List<LocalDate> datesDesc, LocalDate today) {
        if (datesDesc.isEmpty()) {
            return 0;
        }
        LocalDate expected = datesDesc.get(0).equals(today) ? today : today.minusDays(1);
        int streak = 0;
        for (LocalDate date : datesDesc) {
            if (!date.equals(expected)) {
                break;
            }
            streak++;
            expected = expected.minusDays(1);
        }
        return streak;
    }
}
