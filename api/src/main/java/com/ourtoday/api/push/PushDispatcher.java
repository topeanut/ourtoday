package com.ourtoday.api.push;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
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

    /** 요청 처리 후 이벤트 알림용. 요청 스레드를 막지 않게 푸시 전용 실행기에서 보낸다 */
    @Async(PushConfig.EXECUTOR)
    public void sendToUsers(Collection<UUID> userIds, PushMessage message) {
        sendToUsersNow(userIds, message);
    }

    /**
     * 호출한 스레드에서 바로 보낸다. 커플 수만큼 한꺼번에 보내는 스케줄러용 —
     * 비동기 큐(100건)를 넘쳐 버려지거나 실시간 알림을 밀어내지 않도록
     */
    public void sendToUsersNow(Collection<UUID> userIds, PushMessage message) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<PushToken> tokens;
        try {
            tokens = tokenRepository.findByUserIdIn(userIds);
        } catch (RuntimeException e) {
            log.warn("푸시 토큰 조회 실패 type={}", message.type(), e);
            return;
        }
        // 토큰 하나가 실패해도 나머지 발송은 계속
        for (PushToken t : tokens) {
            try {
                if (sender.send(t.getToken(), message) == SendResult.INVALID_TOKEN) {
                    tokenRepository.deleteByToken(t.getToken());
                }
            } catch (RuntimeException e) {
                log.warn("푸시 발송 실패 type={}", message.type(), e);
            }
        }
    }
}
