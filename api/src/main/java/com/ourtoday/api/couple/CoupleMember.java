package com.ourtoday.api.couple;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * 유저당 활성 커플 1개 제약은 CoupleService 에서 보장
 */
@Entity
@Table(name = "couple_members", indexes = @Index(name = "idx_couple_members_user_id", columnList = "user_id"))
@IdClass(CoupleMember.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoupleMember {

    @Id
    @Column(name = "couple_id")
    private UUID coupleId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    public static CoupleMember of(UUID coupleId, UUID userId) {
        CoupleMember member = new CoupleMember();
        member.coupleId = coupleId;
        member.userId = userId;
        return member;
    }

    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private UUID coupleId;
        private UUID userId;
    }
}
