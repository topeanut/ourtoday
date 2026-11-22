package app.ourtoday

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM 수신. 앱이 백그라운드면 notification 메시지는 FCM 이 직접 표시하고(탭 → launcher intent extras 에 data),
 * 포그라운드면 여기로 오므로 직접 표시
 */
class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushBridge.onNewToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"] ?: return
        val path = PushBridge.safePath(message.data[PushBridge.EXTRA_PATH])

        val notifications = NotificationManagerCompat.from(this)
        if (!notifications.areNotificationsEnabled()) return
        PushBridge.ensureChannel(this)

        val open = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            path?.let { putExtra(PushBridge.EXTRA_PATH, it) }
        }
        val requestCode = System.currentTimeMillis().toInt()
        val pending = PendingIntent.getActivity(
            this, requestCode, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, PushBridge.CHANNEL_RECORDS)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(ContextCompat.getColor(this, R.color.brand))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            notifications.notify(requestCode, notification)
        } catch (_: SecurityException) {
            // 그 사이 권한이 회수됨
        }
    }
}
