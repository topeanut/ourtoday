package com.ourtoday.api.location;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface LocationDailyRepository extends JpaRepository<LocationDaily, UUID> {

    Optional<LocationDaily> findByUserIdAndDate(UUID userId, LocalDate date);
}
