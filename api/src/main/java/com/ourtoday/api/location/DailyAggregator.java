package com.ourtoday.api.location;

import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.location.dto.LatLng;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 전일(KST) 포인트를 사용자별 요약(단순화 polyline)으로 upsert. 포인트가 없는 사용자는 만들지 않는다.
 */
@Component
@RequiredArgsConstructor
public class DailyAggregator {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** Douglas-Peucker 허용 오차(도). 0.0001도 ≈ 11m */
    static final double SIMPLIFY_EPSILON_DEGREES = 0.0001;

    private final LocationPointRepository pointRepository;
    private final LocationDailyRepository dailyRepository;
    private final CoupleService coupleService;
    private final Clock clock;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void aggregateYesterday() {
        aggregate(LocalDate.now(clock.withZone(KST)).minusDays(1));
    }

    @Transactional
    public void aggregate(LocalDate date) {
        Instant from = date.atStartOfDay(KST).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(KST).toInstant();
        for (UUID userId : pointRepository.findDistinctUserIdsCapturedBetween(from, to)) {
            List<LocationPoint> points = pointRepository
                .findByUserIdAndCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtAsc(userId, from, to);
            if (points.isEmpty()) {
                continue;
            }
            List<LatLng> path = points.stream().map(p -> new LatLng(p.getLat(), p.getLng())).toList();
            String polyline = PolylineSimplifier.encode(PolylineSimplifier.simplify(path, SIMPLIFY_EPSILON_DEGREES));
            Instant firstAt = points.get(0).getCapturedAt();
            Instant lastAt = points.get(points.size() - 1).getCapturedAt();
            // 해제 시 원본 포인트가 지워지므로 남아 있는 포인트는 현재 커플 기간의 것 → 현재 커플로 귀속
            UUID coupleId = coupleService.findConnectedCoupleId(userId).orElse(null);
            dailyRepository.findByUserIdAndDate(userId, date).ifPresentOrElse(
                d -> {
                    d.update(coupleId, points.size(), firstAt, lastAt, polyline);
                    dailyRepository.save(d); // 자기호출로 트랜잭션이 없어도 반영되도록 명시 저장
                },
                () -> dailyRepository.save(
                    LocationDaily.of(userId, coupleId, date, points.size(), firstAt, lastAt, polyline)));
        }
    }
}
