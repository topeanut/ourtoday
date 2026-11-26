package com.ourtoday.api.location;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 위치 공유 ON/OFF 변경 이력 (실제 상태 변경마다 1행 append)
 */
@Entity
@Table(name = "location_share_events", indexes = @Index(name = "idx_location_share_events_user_at", columnList = "user_id, at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationShareEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "at", nullable = false)
    private Instant at;

    public static LocationShareEvent of(UUID userId, boolean enabled, Instant at) {
        LocationShareEvent event = new LocationShareEvent();
        event.userId = userId;
        event.enabled = enabled;
        event.at = at;
        return event;
    }
}
