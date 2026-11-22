package app.ourtoday

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject

/**
 * 푸시 권한·토큰 조회와 웹으로 보낼 "ourtoday:push" 이벤트 JS 생성
 *
 * 웹 계약(web/src/lib/push.ts): detail = { permission: granted|denied|default, token, platform: "android" }
 */
object PushBridge {

    const val CHANNEL_RECORDS = "records"
    /** 알림 탭 → 열 경로 (FCM data.path, 포그라운드 알림 PendingIntent extra 모두 같은 키) */
    const val EXTRA_PATH = "path"

    private const val PREFS = "push"
    private const val KEY_TOKEN = "token"
    private const val KEY_ASKED = "permission_asked"

    /** 살아 있는 MainActivity 가 등록. 토큰이 바뀌면 메인 스레드에서 호출 */
    @Volatile var tokenListener: ((String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_RECORDS) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RECORDS,
                context.getString(R.string.notification_channel_records),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    /** Android 13+ 에서 시스템 권한 요청이 필요한지 */
    fun needsRuntimePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    /**
     * 권한 대화상자 결과가 사용자의 실제 결정인지.
     * 허용 → 결정. 처음 거부하면 이후 rationale 이 true, 두 번째 거부(다시 묻지 않음)는 요청 전 rationale 이 true.
     * 요청 전·후 모두 false 인 거부는 뒤로가기·바깥 탭으로 닫은 것 → 결정 아님
     */
    fun isRealDecision(granted: Boolean, rationaleBefore: Boolean, rationaleAfter: Boolean): Boolean =
        granted || rationaleAfter || rationaleBefore

    fun markAsked(context: Context) {
        prefs(context).edit { putBoolean(KEY_ASKED, true) }
    }

    fun permission(activity: Activity): String {
        if (NotificationManagerCompat.from(activity).areNotificationsEnabled()) return "granted"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return "denied"
        if (!needsRuntimePermission(activity)) return "denied" // 권한은 있는데 설정에서 알림을 끔
        val askedBefore = prefs(activity).getBoolean(KEY_ASKED, false) ||
            activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        return if (askedBefore) "denied" else "default"
    }

    /** 저장된 토큰이 있으면 바로, 없으면 FCM 에서 받아 저장 후 전달 (실패 시 null). 콜백은 메인 스레드 */
    fun token(context: Context, callback: (String?) -> Unit) {
        val app = context.applicationContext
        prefs(app).getString(KEY_TOKEN, null)?.let { return callback(it) }
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            val token = if (task.isSuccessful) task.result?.takeIf { it.isNotEmpty() } else null
            token?.let { saveToken(app, it) }
            mainHandler.post { callback(token) }
        }
    }

    /** PushService.onNewToken (백그라운드 스레드) */
    fun onNewToken(context: Context, token: String) {
        saveToken(context.applicationContext, token)
        mainHandler.post { tokenListener?.invoke(token) }
    }

    fun eventScript(permission: String, token: String?): String {
        val detail = JSONObject()
            .put("permission", permission)
            .put("token", token ?: JSONObject.NULL)
            .put("platform", "android")
        return "window.dispatchEvent(new CustomEvent(\"ourtoday:push\", { detail: $detail }));"
    }

    /** 네이티브가 여는 경로는 "/" 로 시작하고 "//" 로 시작하지 않는 우리 앱 경로만 */
    fun safePath(path: String?): String? =
        path?.takeIf { it.startsWith("/") && !it.startsWith("//") && !it.contains('\\') }

    private fun saveToken(context: Context, token: String) {
        prefs(context).edit { putString(KEY_TOKEN, token) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
