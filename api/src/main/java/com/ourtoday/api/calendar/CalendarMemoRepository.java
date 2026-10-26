package com.ourtoday.api.calendar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CalendarMemoRepository extends JpaRepository<CalendarMemo, UUID> {

    List<CalendarMemo> findByCoupleIdAndDateBetweenOrderByDateAscCreatedAtAsc(UUID coupleId, LocalDate from, LocalDate to);

    long countByCoupleIdAndAuthorId(UUID coupleId, UUID authorId);

    List<CalendarMemo> findByCoupleIdOrderByDateAscCreatedAtAsc(UUID coupleId);

    void deleteByAuthorId(UUID authorId);
}
