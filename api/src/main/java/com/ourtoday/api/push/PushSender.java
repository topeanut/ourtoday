package com.ourtoday.api.push;

public interface PushSender {

    SendResult send(String token, PushMessage message);

    /** 알림 없이 앱만 깨우는 무음(data-only) 고우선순위 푸시 */
    SendResult sendData(String token, DataPush push);
}
