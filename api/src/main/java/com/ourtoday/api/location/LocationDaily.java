package com.ourtoday.api.location;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 사용자별 일자(KST) 위치 요약 (영구 보관, 원본 포인트 90일 삭제 이후에도 경로 조회용)
 */
@Entity
@Table(name = "location_daily",
    uniqueConstraints = @UniqueConstraint(name = "uk_location_daily_user_date", columnNames = {"user_id", "date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 집계 시점의 커플. 상대 경로 조회는 현재 커플과 같은 요약만 돌려준다
     * (해제 후 새 상대에게 이전 관계 시절 경로가 노출되지 않도록). 커플이 없을 때 쌓인 요약은 null → 누구에게도 안 보임
     */
    @Column(name = "couple_id")
    private UUID coupleId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "point_count", nullable = false)
    private int pointCount;

    @Column(name = "first_at", nullable = false)
    private Instant firstAt;

    @Column(name = "last_at", nullable = false)
    private Instant lastAt;

    @Column(nullable = false, columnDefinition = "text")
    private String polyline;

    public static LocationDaily of(UUID userId, UUID coupleId, LocalDate date, int pointCount,
                                   Instant firstAt, Instant lastAt, String polyline) {
        LocationDaily d = new LocationDaily();
        d.userId = userId;
        d.date = date;
        d.update(coupleId, pointCount, firstAt, lastAt, polyline);
        return d;
    }

    public void update(UUID coupleId, int pointCount, Instant firstAt, Instant lastAt, String polyline) {
        this.coupleId = coupleId;
        this.pointCount = pointCount;
        this.firstAt = firstAt;
        this.lastAt = lastAt;
        this.polyline = polyline;
    }
}
