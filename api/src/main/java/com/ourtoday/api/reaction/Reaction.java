package com.ourtoday.api.reaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reactions",
    uniqueConstraints = @UniqueConstraint(name = "uk_reactions_post_user_emoji", columnNames = {"post_id", "user_id", "emoji_type"}),
    indexes = @Index(name = "idx_reactions_post", columnList = "post_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "emoji_type", nullable = false, length = 20)
    private EmojiType emojiType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Reaction of(UUID postId, UUID userId, EmojiType emojiType) {
        Reaction reaction = new Reaction();
        reaction.postId = postId;
        reaction.userId = userId;
        reaction.emojiType = emojiType;
        return reaction;
    }
}
