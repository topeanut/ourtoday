package com.ourtoday.api.push;

import lombok.extern.slf4j.Slf4j;

/**
 * Firebase 서비스 계정이 없을 때(로컬 개발 등) 쓰는 발송기. 보내지 않고 로그만 남김
 */
@Slf4j
public class NoopPushSender implements PushSender {

    @Override
    public SendResult send(String token, PushMessage message) {
        log.debug("푸시 비활성 — 발송 생략 type={}", message.type());
        return SendResult.OK;
    }

    @Override
    public SendResult sendData(String token, DataPush push) {
        log.debug("푸시 비활성 — 무음 발송 생략 type={}", push.type());
        return SendResult.OK;
    }
}
