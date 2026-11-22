package com.ourtoday.api.push;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 테스트용 발송기. 모든 SpringBootTest 에서 실제 발송기 대신 쓰이며, 보낸 (token, message) 를 기록한다.
 * 테스트 시작 전에 reset() 으로 비울 것
 */
@Primary
@Component
public class FakePushSender implements PushSender {

    public record Sent(String token, PushMessage message) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private final Set<String> invalidTokens = new CopyOnWriteArraySet<>();
    private volatile RuntimeException failure;

    @Override
    public SendResult send(String token, PushMessage message) {
        if (failure != null) {
            throw failure;
        }
        sent.add(new Sent(token, message));
        return invalidTokens.contains(token) ? SendResult.INVALID_TOKEN : SendResult.OK;
    }

    public List<Sent> sent() {
        return new ArrayList<>(sent);
    }

    public void markInvalid(String token) {
        invalidTokens.add(token);
    }

    public void failWith(RuntimeException e) {
        this.failure = e;
    }

    public void reset() {
        sent.clear();
        invalidTokens.clear();
        failure = null;
    }
}
