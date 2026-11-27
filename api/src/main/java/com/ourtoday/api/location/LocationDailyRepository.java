package com.ourtoday.api.location;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface LocationDailyRepository extends JpaRepository<LocationDaily, UUID> {

    void deleteByUserId(UUID userId);

    Optional<LocationDaily> findByUserIdAndDate(UUID userId, LocalDate date);

    Optional<LocationDaily> findByUserIdAndDateAndCoupleId(UUID userId, LocalDate date, UUID coupleId);
}
