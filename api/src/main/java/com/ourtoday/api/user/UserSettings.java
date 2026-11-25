package com.ourtoday.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSettings {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "notify_memory_today", nullable = false)
    private boolean notifyMemoryToday = true;

    @Column(name = "notify_partner_post", nullable = false)
    private boolean notifyPartnerPost = true;

    @Column(name = "notify_reaction", nullable = false, columnDefinition = "boolean not null default true")
    private boolean notifyReaction = true;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserSettings createDefault(User user) {
        UserSettings settings = new UserSettings();
        settings.user = user;
        return settings;
    }

    /** null 인 항목은 기존 값을 유지한다. */
    public void updateNotificationSettings(Boolean memoryToday, Boolean partnerPost, Boolean reaction) {
        if (memoryToday != null) this.notifyMemoryToday = memoryToday;
        if (partnerPost != null) this.notifyPartnerPost = partnerPost;
        if (reaction != null) this.notifyReaction = reaction;
    }
}
