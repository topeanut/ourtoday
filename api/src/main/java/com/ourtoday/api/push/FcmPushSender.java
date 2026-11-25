package com.ourtoday.api.push;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FcmPushSender implements PushSender {

    static final String TITLE = "오늘의 우리";
    static final String ANDROID_CHANNEL_ID = "records";

    private final FirebaseMessaging messaging;

    public FcmPushSender(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    @Override
    public SendResult send(String token, PushMessage message) {
        Message fcm = Message.builder()
            .setToken(token)
            .setNotification(Notification.builder().setTitle(TITLE).setBody(message.body()).build())
            .putData("path", message.path())
            .putData("type", message.type())
            .setAndroidConfig(AndroidConfig.builder()
                .setNotification(AndroidNotification.builder().setChannelId(ANDROID_CHANNEL_ID).build())
                .build())
            .setApnsConfig(ApnsConfig.builder()
                .setAps(Aps.builder()
                    .setAlert(ApsAlert.builder().setTitle(TITLE).setBody(message.body()).build())
                    .setSound("default")
                    .build())
                .build())
            .build();
        try {
            messaging.send(fcm);
            return SendResult.OK;
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                return SendResult.INVALID_TOKEN;
            }
            log.warn("FCM 발송 실패 code={}", code, e);
            return SendResult.FAILED;
        } catch (RuntimeException e) {
            log.warn("FCM 발송 중 예외", e);
            return SendResult.FAILED;
        }
    }
}
