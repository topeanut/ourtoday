package com.ourtoday.api.push;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushTokenRepository extends JpaRepository<PushToken, UUID> {

    Optional<PushToken> findByToken(String token);

    List<PushToken> findByUserIdIn(Collection<UUID> userIds);

    @Transactional
    void deleteByToken(String token);

    void deleteByUserId(UUID userId);
}
