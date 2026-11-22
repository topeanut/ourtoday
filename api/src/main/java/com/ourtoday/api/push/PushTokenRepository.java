package com.ourtoday.api.push;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushTokenRepository extends JpaRepository<PushToken, UUID> {

    Optional<PushToken> findByToken(String token);

    List<PushToken> findByUserIdIn(Collection<UUID> userIds);

    void deleteByToken(String token);

    void deleteByUserId(UUID userId);
}
