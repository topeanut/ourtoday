package com.ourtoday.api.location;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LocationShareEventRepository extends JpaRepository<LocationShareEvent, UUID> {

    Optional<LocationShareEvent> findTopByUserIdAndAtLessThanEqualOrderByAtDesc(UUID userId, Instant at);
}
