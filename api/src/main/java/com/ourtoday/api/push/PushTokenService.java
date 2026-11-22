package com.ourtoday.api.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Slf4j
@Service
public class PushTokenService {

    private final PushTokenRepository repository;
    private final TransactionTemplate tx;

    public PushTokenService(PushTokenRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * 같은 토큰이 있으면 계정·플랫폼만 갱신 (기기에서 계정이 바뀐 경우 새 계정으로 이동).
     * 앱이 같은 토큰을 거의 동시에 두 번 보내면 둘 다 "없음" 을 보고 insert 해 unique 위반이 난다.
     * 그때는 실패한 트랜잭션을 버리고 새 트랜잭션에서 다시 읽어 갱신한다 (먼저 들어간 행을 덮어씀).
     * 트랜잭션 밖(컨트롤러)에서 호출하는 것을 전제로 하며, 바깥 트랜잭션이 있으면 거기에 합류한다
     */
    public void register(UUID userId, String token, PushPlatform platform) {
        try {
            tx.executeWithoutResult(s -> upsert(userId, token, platform));
        } catch (DataIntegrityViolationException e) {
            log.info("푸시 토큰 동시 등록 감지 → 다시 읽어 갱신");
            tx.executeWithoutResult(s -> upsert(userId, token, platform));
        }
    }

    private void upsert(UUID userId, String token, PushPlatform platform) {
        repository.findByToken(token).ifPresentOrElse(
            existing -> existing.reassign(userId, platform),
            // flush 해서 unique 위반을 이 트랜잭션 안에서 드러냄 (커밋 시점이 아니라)
            () -> repository.saveAndFlush(PushToken.create(userId, token, platform)));
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
