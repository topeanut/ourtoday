package com.ourtoday.api.bucket;

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
import java.time.LocalDate;
import java.util.UUID;

/**
 * 데이트 버킷리스트 (커플 둘 다 수정·삭제·완료 가능)
 */
@Entity
@Table(name = "bucket_items", indexes = @Index(name = "idx_bucket_items_couple", columnList = "couple_id, completed"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BucketItem {

    public static final int TITLE_MAX_LENGTH = 100;
    public static final int DESCRIPTION_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "couple_id", nullable = false)
    private UUID coupleId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(nullable = false)
    private boolean completed;

    /** 완료 시 연결한 게시글 (선택) */
    @Column(name = "completed_post_id")
    private UUID completedPostId;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static BucketItem create(UUID coupleId, UUID createdBy, String title, String description,
                                    LocalDate targetDate) {
        BucketItem item = new BucketItem();
        item.coupleId = coupleId;
        item.createdBy = createdBy;
        item.update(title, description, targetDate);
        return item;
    }

    public void update(String title, String description, LocalDate targetDate) {
        this.title = title;
        this.description = description;
        this.targetDate = targetDate;
    }

    /** 이미 완료된 항목이면 연결 게시글만 바꾸고 완료 시각은 유지 */
    public void complete(UUID postId, Instant now) {
        if (!completed) {
            completedAt = now;
        }
        completed = true;
        completedPostId = postId;
    }

    public void uncomplete() {
        completed = false;
        completedPostId = null;
        completedAt = null;
    }
}
