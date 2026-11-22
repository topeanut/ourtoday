package com.ourtoday.api.push;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PushTokenService {

    private final PushTokenRepository repository;

    /** 같은 토큰이 있으면 계정·플랫폼만 갱신 (기기에서 계정이 바뀐 경우 새 계정으로 이동) */
    @Transactional
    public void register(UUID userId, String token, PushPlatform platform) {
        repository.findByToken(token).ifPresentOrElse(
            existing -> existing.reassign(userId, platform),
            () -> repository.save(PushToken.create(userId, token, platform)));
    }

    /** 내 토큰일 때만 삭제 */
    @Transactional
    public void delete(UUID userId, String token) {
        repository.findByToken(token)
            .filter(t -> t.getUserId().equals(userId))
            .ifPresent(repository::delete);
    }

    @Transactional
    public void deleteAllOf(UUID userId) {
        repository.deleteByUserId(userId);
    }
}
