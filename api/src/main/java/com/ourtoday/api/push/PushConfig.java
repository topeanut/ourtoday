package com.ourtoday.api.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.concurrent.Executor;

@Slf4j
@Configuration
public class PushConfig {

    public static final String EXECUTOR = "pushExecutor";

    @Bean
    public PushSender pushSender(@Value("${app.push.firebase-service-account:}") String serviceAccountBase64) {
        // 서비스 계정이 없어도 앱 기동은 막지 않고 푸시만 끔
        if (!StringUtils.hasText(serviceAccountBase64)) {
            log.warn("app.push.firebase-service-account 미설정 → 푸시 발송 비활성");
            return new NoopPushSender();
        }
        try {
            byte[] json = Base64.getDecoder().decode(serviceAccountBase64.trim());
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(json)))
                .build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                ? FirebaseApp.initializeApp(options)
                : FirebaseApp.getInstance();
            return new FcmPushSender(FirebaseMessaging.getInstance(app));
        } catch (IOException e) {
            throw new UncheckedIOException("Firebase 서비스 계정을 읽지 못했습니다.", e);
        }
    }

    /** 발송이 요청 스레드를 막지 않도록 비동기로 (테스트는 app.push.async=false 로 동기 실행) */
    @Configuration
    @EnableAsync
    @ConditionalOnProperty(name = "app.push.async", havingValue = "true")
    static class AsyncConfig {

        @Bean(EXECUTOR)
        Executor pushExecutor() {
            ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
            ex.setCorePoolSize(1);
            ex.setMaxPoolSize(2);
            ex.setQueueCapacity(100);
            ex.setThreadNamePrefix("push-");
            // 큐가 차도 호출자(커밋 후 리스너·스케줄러)에게 예외를 던지지 않고 버림
            ex.setRejectedExecutionHandler((r, executor) -> log.warn("푸시 큐가 가득 차 발송을 건너뜁니다."));
            return ex;
        }
    }
}
