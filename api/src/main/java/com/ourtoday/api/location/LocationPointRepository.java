package com.ourtoday.api.location;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationPointRepository extends JpaRepository<LocationPoint, UUID> {

    Optional<LocationPoint> findTopByUserIdOrderByCapturedAtDesc(UUID userId);

    List<LocationPoint> findByUserIdAndCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtAsc(
        UUID userId, Instant from, Instant to);

    @Query("select distinct p.userId from LocationPoint p where p.capturedAt >= :from and p.capturedAt < :to")
    List<UUID> findDistinctUserIdsCapturedBetween(@Param("from") Instant from, @Param("to") Instant to);

    void deleteByCapturedAtBefore(Instant cutoff);
}
