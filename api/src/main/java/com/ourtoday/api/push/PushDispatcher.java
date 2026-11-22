package com.ourtoday.api.push;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

/**
 * 사용자들의 모든 기기로 푸시를 보낸다. 발송 실패가 본 요청(댓글·기록 저장 등)을 깨뜨리면 안 되므로
 * 예외는 로그만 남기고 삼킨다. 발송은 DB 트랜잭션 밖에서 하고, 무효 토큰 삭제만 repository 트랜잭션으로 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushDispatcher {

    private final PushTokenRepository tokenRepository;
    private final PushSender sender;

    @Async(PushConfig.EXECUTOR)
    public void sendToUsers(Collection<UUID> userIds, PushMessage message) {
        try {
            if (userIds == null || userIds.isEmpty()) {
                return;
            }
            for (PushToken t : tokenRepository.findByUserIdIn(userIds)) {
                SendResult result = sender.send(t.getToken(), message);
                if (result == SendResult.INVALID_TOKEN) {
                    tokenRepository.deleteByToken(t.getToken());
                }
            }
        } catch (RuntimeException e) {
            log.warn("푸시 발송 실패 type={}", message.type(), e);
        }
    }
}
