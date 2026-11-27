package com.ourtoday.api.push;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** 시간을 옮기거나 고정할 수 있는 테스트 전용 Clock. 각 테스트 시작 시 reset() 또는 set() 으로 초기화할 것 */
public class MutableClock extends Clock {

    /**
     * DB(H2/Postgres)는 Instant 를 마이크로초로 저장하며 나노초를 반올림한다.
     * now 를 나노초로 두면 저장값이 반올림되어 원본보다 커질 수 있어, "만료=now" 직후의
     * ExpiresAtAfter(now) 경계 비교가 실제 시각의 나노초에 따라 뒤집히는 플래키가 생긴다.
     * 처음부터 마이크로초로 잘라 DB 정밀도와 일치시킨다.
     */
    private volatile Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    public void advance(Duration d) {
        now = now.plus(d);
    }

    public void set(Instant instant) {
        now = instant;
    }

    public void reset() {
        now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }
}
