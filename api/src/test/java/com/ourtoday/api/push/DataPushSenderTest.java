package com.ourtoday.api.push;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataPushSenderTest {

    private final FirebaseMessaging messaging = mock(FirebaseMessaging.class);
    private final FcmPushSender sender = new FcmPushSender(messaging);
    private final DataPush push = new DataPush("LOCATION_WAKE", Map.of("expiresAt", "123"));

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    @SuppressWarnings("unchecked")
    private static <V> Map<String, V> map(Object o) {
        return (Map<String, V>) o;
    }

    @Test
    void 알림_없이_data만_담아_고우선순위로_보내고_OK를_반환한다() throws Exception {
        SendResult result = sender.sendData("tok", push);

        assertThat(result).isEqualTo(SendResult.OK);
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messaging).send(captor.capture());
        Message m = captor.getValue();
        assertThat(field(m, "token")).isEqualTo("tok");
        assertThat(field(m, "notification")).isNull();
        assertThat(map(field(m, "data")))
            .containsEntry("type", "LOCATION_WAKE")
            .containsEntry("expiresAt", "123")
            .hasSize(2);

        Object android = field(m, "androidConfig");
        assertThat(String.valueOf(field(android, "priority"))).isEqualToIgnoringCase("high");
        assertThat(field(android, "notification")).isNull();

        Object apns = field(m, "apnsConfig");
        assertThat(map(field(apns, "headers")))
            .containsEntry("apns-push-type", "background")
            .containsEntry("apns-priority", "5");
        Map<String, Object> aps = map(map(field(apns, "payload")).get("aps"));
        assertThat(aps).isNotNull();
        assertThat(aps).containsEntry("content-available", 1).doesNotContainKeys("alert", "sound");
    }

    @Test
    void UNREGISTERED면_INVALID_TOKEN() throws Exception {
        failWith(MessagingErrorCode.UNREGISTERED);
        assertThat(sender.sendData("tok", push)).isEqualTo(SendResult.INVALID_TOKEN);
    }

    @Test
    void INVALID_ARGUMENT면_INVALID_TOKEN() throws Exception {
        failWith(MessagingErrorCode.INVALID_ARGUMENT);
        assertThat(sender.sendData("tok", push)).isEqualTo(SendResult.INVALID_TOKEN);
    }

    @Test
    void 그_외_오류는_FAILED() throws Exception {
        failWith(MessagingErrorCode.INTERNAL);
        assertThat(sender.sendData("tok", push)).isEqualTo(SendResult.FAILED);
    }

    @Test
    void Noop_발송기는_OK() {
        assertThat(new NoopPushSender().sendData("tok", push)).isEqualTo(SendResult.OK);
    }

    @Test
    void 디스패처는_무효_토큰을_지운다() {
        PushTokenRepository repo = mock(PushTokenRepository.class);
        PushSender fake = mock(PushSender.class);
        UUID user = UUID.randomUUID();
        PushToken bad = PushToken.create(user, "bad", PushPlatform.ANDROID);
        PushToken good = PushToken.create(user, "good", PushPlatform.IOS);
        when(repo.findByUserIdIn(List.of(user))).thenReturn(List.of(bad, good));
        when(fake.sendData("bad", push)).thenReturn(SendResult.INVALID_TOKEN);
        when(fake.sendData("good", push)).thenReturn(SendResult.OK);

        new PushDispatcher(repo, fake).sendDataToUsers(List.of(user), push);

        verify(fake).sendData("bad", push);
        verify(fake).sendData("good", push);
        verify(repo).deleteByToken("bad");
        org.mockito.Mockito.verify(repo, org.mockito.Mockito.never()).deleteByToken("good");
    }

    private void failWith(MessagingErrorCode code) throws Exception {
        FirebaseMessagingException e = mock(FirebaseMessagingException.class);
        when(e.getMessagingErrorCode()).thenReturn(code);
        when(messaging.send(any(Message.class))).thenThrow(e);
    }
}
