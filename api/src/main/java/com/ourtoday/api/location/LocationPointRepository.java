package com.ourtoday.api.location;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LocationPointRepository extends JpaRepository<LocationPoint, UUID> {

    Optional<LocationPoint> findTopByUserIdOrderByCapturedAtDesc(UUID userId);

    void deleteByCapturedAtBefore(Instant cutoff);
}
