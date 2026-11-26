package com.ourtoday.api.location;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PreciseRequestRepository extends JpaRepository<PreciseRequest, UUID> {

    void deleteByRequesterIdOrTargetId(UUID requesterId, UUID targetId);

    boolean existsByTargetIdAndExpiresAtAfter(UUID targetId, Instant now);

    List<PreciseRequest> findByRequesterIdAndTargetIdAndExpiresAtAfter(UUID requesterId, UUID targetId, Instant now);
}
