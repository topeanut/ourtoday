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
        return dispatch(fcm);
    }

    /** 알림·alert 없이 data 만 담은 고우선순위 푸시. iOS 는 background(content-available)로 깨운다 */
    @Override
    public SendResult sendData(String token, DataPush push) {
        Message fcm = Message.builder()
            .setToken(token)
            .putAllData(push.data())
            .putData("type", push.type())
            .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
            .setApnsConfig(ApnsConfig.builder()
                .putHeader("apns-push-type", "background")
                // background 푸시는 priority 5 만 허용 (10 은 iOS 가 거부)
                .putHeader("apns-priority", "5")
                .setAps(Aps.builder().setContentAvailable(true).build())
                .build())
            .build();
        return dispatch(fcm);
    }

    private SendResult dispatch(Message fcm) {
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
