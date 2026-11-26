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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 상대에게 정밀 위치를 요청한 기록. expires_at 이 지나면 비활성(삭제 불필요)
 */
@Entity
@Table(name = "location_precise_requests", indexes = {
    @Index(name = "idx_precise_requests_target_expires", columnList = "target_id, expires_at"),
    @Index(name = "idx_precise_requests_requester_target", columnList = "requester_id, target_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreciseRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PreciseRequest of(UUID requesterId, UUID targetId, Instant expiresAt) {
        PreciseRequest r = new PreciseRequest();
        r.requesterId = requesterId;
        r.targetId = targetId;
        r.expiresAt = expiresAt;
        return r;
    }

    public void expireAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
