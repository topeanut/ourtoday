package com.ourtoday.api.location;

import com.ourtoday.api.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 클래스 레벨 @Transactional 없이 스케줄 진입점(aggregateYesterday)을 실행 — 프록시 자기호출 회귀 방지 */
@SpringBootTest
class DailyAggregatorRerunTest {

    @Autowired DailyAggregator aggregator;
    @Autowired LocationPointRepository pointRepository;
    @Autowired LocationDailyRepository dailyRepository;
    @Autowired UserService userService;

    @Test
    void 재집계하면_기존_요약_값이_갱신된다() {
        UUID userId = userService.findOrCreateByKakaoId("kakao-" + UUID.randomUUID()).getId();
        ZoneId kst = ZoneId.of("Asia/Seoul");
        LocalDate yesterday = LocalDate.now(kst).minusDays(1);
        try {
            pointRepository.save(point(userId, yesterday, 9, 0, 37.0, 127.0));
            pointRepository.save(point(userId, yesterday, 9, 10, 37.001, 127.001));
            aggregator.aggregateYesterday();
            assertThat(dailyRepository.findByUserIdAndDate(userId, yesterday).orElseThrow().getPointCount()).isEqualTo(2);

            pointRepository.save(point(userId, yesterday, 9, 20, 38.0, 128.0));
            aggregator.aggregateYesterday();

            LocationDaily d = dailyRepository.findByUserIdAndDate(userId, yesterday).orElseThrow();
            assertThat(d.getPointCount()).isEqualTo(3);
            assertThat(d.getPolyline()).contains("38.0,128.0");
        } finally {
            dailyRepository.findByUserIdAndDate(userId, yesterday).ifPresent(dailyRepository::delete);
            pointRepository.findAll().stream().filter(p -> p.getUserId().equals(userId)).forEach(pointRepository::delete);
        }
    }

    private LocationPoint point(UUID userId, LocalDate day, int h, int m, double lat, double lng) {
        return LocationPoint.of(userId, lat, lng, null,
            day.atTime(h, m).atZone(ZoneId.of("Asia/Seoul")).toInstant(), PointSource.COARSE);
    }
}
