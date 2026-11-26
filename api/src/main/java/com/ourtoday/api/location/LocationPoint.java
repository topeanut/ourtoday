package com.ourtoday.api.location;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 위치 포인트 (공유 ON 구간에 수집된 점만 저장, 90일 후 삭제)
 */
@Entity
@Table(name = "location_points", indexes = @Index(name = "idx_location_points_user_captured", columnList = "user_id, captured_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    private Double accuracy;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointSource source;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static LocationPoint of(UUID userId, double lat, double lng, Double accuracy,
                                   Instant capturedAt, PointSource source) {
        LocationPoint p = new LocationPoint();
        p.userId = userId;
        p.lat = lat;
        p.lng = lng;
        p.accuracy = accuracy;
        p.capturedAt = capturedAt;
        p.source = source;
        return p;
    }
}
