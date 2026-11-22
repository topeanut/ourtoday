package com.ourtoday.api.post;

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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 3줄 일기
 */
@Entity
@Table(name = "posts", indexes = {
    @Index(name = "idx_posts_couple_date", columnList = "couple_id, posted_date"),
    @Index(name = "idx_posts_couple_author", columnList = "couple_id, author_id, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    public static final int CONTENT_MAX_LENGTH = 300;
    public static final int MAX_IMAGES = 4;
    public static final int PLACE_NAME_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "couple_id", nullable = false)
    private UUID coupleId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false, length = CONTENT_MAX_LENGTH)
    private String content;

    /** 기록 대상 날짜 (작년 오늘 조회 기준) */
    @Column(name = "posted_date", nullable = false)
    private LocalDate postedDate;

    @Column(name = "place_lat")
    private Double placeLat;

    @Column(name = "place_lng")
    private Double placeLng;

    @Column(name = "place_name", length = PLACE_NAME_MAX_LENGTH)
    private String placeName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Post create(UUID coupleId, UUID authorId, String content, LocalDate postedDate,
                              Double placeLat, Double placeLng, String placeName) {
        Post post = new Post();
        post.coupleId = coupleId;
        post.authorId = authorId;
        post.content = content;
        post.postedDate = postedDate;
        post.updateLocation(placeLat, placeLng, placeName);
        return post;
    }

    public void update(String content, LocalDate postedDate) {
        this.content = content;
        this.postedDate = postedDate;
    }

    public void updateLocation(Double placeLat, Double placeLng, String placeName) {
        this.placeLat = placeLat;
        this.placeLng = placeLng;
        this.placeName = placeName;
    }

    public boolean isWrittenBy(UUID userId) {
        return authorId.equals(userId);
    }
}
