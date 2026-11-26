package app.ourtoday

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * 정밀(PRECISE) 위치 수집 포그라운드 서비스. 시작 후 3분이 지나면 자동 종료,
 * 이미 실행 중이면 타이머만 연장한다. ACTION_STOP 으로 즉시 종료.
 */
class LocationForegroundService : Service() {

    companion object {
        const val ACTION_STOP = "app.ourtoday.action.STOP_LOCATION"
        private const val CHANNEL_ID = "location_sharing"
        private const val NOTIF_ID = 4101
        private const val RUN_MS = 3 * 60_000L
        private const val INTERVAL_MS = 5_000L

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, LocationForegroundService::class.java))
            } catch (_: Exception) {
                // 백그라운드 시작 제한(ForegroundServiceStartNotAllowedException 등) — 무시
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, LocationForegroundService::class.java).setAction(ACTION_STOP))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopSelf() }
    private var callback: LocationCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        LocationEngine.init(this)

        // 이미 실행 중이면 타이머만 리셋
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, RUN_MS)
        if (callback != null) return START_NOT_STICKY

        if (!goForeground() || !LocationEngine.isSharing || !LocationEngine.hasLocationPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startUpdates()
        return START_NOT_STICKY
    }

    /** targetSdk 34+: 반드시 위치 타입과 함께 startForeground. 실패해도 크래시 없이 false */
    private fun goForeground(): Boolean {
        return try {
            ensureChannel()
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
            true
        } catch (_: Exception) {
            // ForegroundServiceStartNotAllowedException / SecurityException (백그라운드 위치 미허용 등)
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun startUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(INTERVAL_MS / 2)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { LocationEngine.bufferAndUpload(it, "PRECISE") }
            }
        }
        try {
            LocationServices.getFusedLocationProviderClient(this)
                .requestLocationUpdates(request, cb, Looper.getMainLooper())
            callback = cb
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "위치 공유", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_notify)
        .setColor(ContextCompat.getColor(this, R.color.brand))
        .setContentTitle("위치 공유 중")
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .build()

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        callback?.let { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(it) }
        callback = null
        super.onDestroy()
    }
}
