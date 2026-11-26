package com.ourtoday.api.location;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LocationAccessLogRepository extends JpaRepository<LocationAccessLog, UUID> {
}
