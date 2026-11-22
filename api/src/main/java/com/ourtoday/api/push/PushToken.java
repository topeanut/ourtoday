package com.ourtoday.api.push;

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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 기기 푸시 토큰 (FCM). 토큰 하나는 한 계정에만 속함
 */
@Entity
@Table(name = "push_tokens", indexes = @Index(name = "idx_push_tokens_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushToken {

    public static final int TOKEN_MAX_LENGTH = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, length = TOKEN_MAX_LENGTH)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PushPlatform platform;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PushToken create(UUID userId, String token, PushPlatform platform) {
        PushToken t = new PushToken();
        t.token = token;
        t.reassign(userId, platform);
        return t;
    }

    public void reassign(UUID userId, PushPlatform platform) {
        this.userId = userId;
        this.platform = platform;
    }
}
