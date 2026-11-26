package com.ourtoday.api.location;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LocationShareRepository extends JpaRepository<LocationShare, UUID> {
}
