package com.ourtoday.api.couple;

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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "couples", indexes = @Index(name = "idx_couples_invite_code", columnList = "invite_code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Couple {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 두 번째 멤버 합류 시 NULL */
    @Column(name = "invite_code", unique = true, length = 8)
    private String inviteCode;

    @Column(name = "invite_code_expires_at")
    private Instant inviteCodeExpiresAt;

    /** 사귄 날짜. 합류 시점으로 세팅, 마이페이지에서 수정 */
    @Column(name = "started_at")
    private LocalDate startedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CoupleStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Couple createPending(String inviteCode, Instant expiresAt) {
        Couple couple = new Couple();
        couple.status = CoupleStatus.PENDING;
        couple.inviteCode = inviteCode;
        couple.inviteCodeExpiresAt = expiresAt;
        return couple;
    }

    public void reissueInviteCode(String inviteCode, Instant expiresAt) {
        this.inviteCode = inviteCode;
        this.inviteCodeExpiresAt = expiresAt;
    }

    public boolean isInviteExpired(Instant now) {
        return inviteCodeExpiresAt == null || !inviteCodeExpiresAt.isAfter(now);
    }

    public void connect(LocalDate startedAt) {
        this.status = CoupleStatus.CONNECTED;
        this.startedAt = startedAt;
        this.inviteCode = null;
        this.inviteCodeExpiresAt = null;
    }

    public void updateStartedAt(LocalDate startedAt) {
        this.startedAt = startedAt;
    }

    /** 해제: 멤버 연결은 서비스에서 끊고, 기록은 남겨 둠 */
    public void breakUp() {
        this.status = CoupleStatus.BROKEN;
        this.inviteCode = null;
        this.inviteCodeExpiresAt = null;
    }

    public boolean isConnected() {
        return status == CoupleStatus.CONNECTED;
    }
}
