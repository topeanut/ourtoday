package com.ourtoday.api.location;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 사용자별 위치 공유 ON/OFF 현재 상태
 */
@Entity
@Table(name = "location_shares")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationShare {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private boolean enabled;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static LocationShare of(UUID userId, boolean enabled) {
        LocationShare share = new LocationShare();
        share.userId = userId;
        share.enabled = enabled;
        return share;
    }

    public void changeTo(boolean enabled) {
        this.enabled = enabled;
    }
}
