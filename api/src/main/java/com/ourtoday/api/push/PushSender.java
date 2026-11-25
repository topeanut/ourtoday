package com.ourtoday.api.push;

public interface PushSender {

    SendResult send(String token, PushMessage message);
}
