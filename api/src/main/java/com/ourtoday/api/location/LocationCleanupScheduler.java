package com.ourtoday.api.location;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class LocationCleanupScheduler {

    private static final int RETENTION_DAYS = 90;

    private final LocationPointRepository pointRepository;
    private final Clock clock;

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpiredPoints() {
        pointRepository.deleteByCapturedAtBefore(clock.instant().minus(RETENTION_DAYS, ChronoUnit.DAYS));
    }
}
