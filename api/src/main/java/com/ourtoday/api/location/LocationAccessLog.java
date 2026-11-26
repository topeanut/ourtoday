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

import java.time.Instant;
import java.util.UUID;

/**
 * 상대 위치를 본(요청한) 접근 이력. 정밀 요청 성공마다 1행 append
 */
@Entity
@Table(name = "location_access_log", indexes = @Index(name = "idx_location_access_log_target_started", columnList = "target_id, started_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "viewer_id", nullable = false)
    private UUID viewerId;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccessKind kind;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    public static LocationAccessLog of(UUID viewerId, UUID targetId, AccessKind kind, Instant startedAt) {
        LocationAccessLog log = new LocationAccessLog();
        log.viewerId = viewerId;
        log.targetId = targetId;
        log.kind = kind;
        log.startedAt = startedAt;
        return log;
    }
}
