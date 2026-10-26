package com.ourtoday.api.calendar;

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
 * 캘린더 메모/일정 (커플 공유)
 */
@Entity
@Table(name = "calendar_memos", indexes = @Index(name = "idx_calendar_memos_couple_date", columnList = "couple_id, memo_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarMemo {

    public static final int TITLE_MAX_LENGTH = 50;
    public static final int DESCRIPTION_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "couple_id", nullable = false)
    private UUID coupleId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    /** 표시 날짜 (date 는 예약어라 컬럼명 memo_date) */
    @Column(name = "memo_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(length = DESCRIPTION_MAX_LENGTH)
    private String description;

    /** #RRGGBB */
    @Column(length = 7)
    private String color;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static CalendarMemo create(UUID coupleId, UUID authorId, LocalDate date, String title,
                                      String description, String color) {
        CalendarMemo memo = new CalendarMemo();
        memo.coupleId = coupleId;
        memo.authorId = authorId;
        memo.update(date, title, description, color);
        return memo;
    }

    public void update(LocalDate date, String title, String description, String color) {
        this.date = date;
        this.title = title;
        this.description = description;
        this.color = color;
    }

    public boolean isWrittenBy(UUID userId) {
        return authorId.equals(userId);
    }
}
