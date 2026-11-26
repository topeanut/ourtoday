package app.ourtoday

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * 위치 공유 엔진.
 * - loose(COARSE): 포그라운드 서비스 없이 저전력 주기 수집. 백그라운드 전달은 OS 재량(best-effort)
 * - precise(PRECISE): LocationForegroundService 가 고정확도 수집 후 bufferAndUpload 재사용
 * 모든 공개 함수는 메인 스레드에서 호출해도 안전하며 네트워크는 단일 백그라운드 Executor 에서 수행
 */
object LocationEngine {

    private const val PREFS = "location_engine"
    private const val KEY_SHARING = "is_sharing"
    private const val MAX_BUFFER = 300
    private const val LOOSE_INTERVAL_MS = 5 * 60_000L

    private val executor = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private val buffer = ArrayDeque<PointDTO>()

    @Volatile private var appContext: Context? = null
    private var looseCallback: LocationCallback? = null

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        TokenStore.init(app)
    }

    private fun prefs(): SharedPreferences? =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var isSharing: Boolean
        get() = prefs()?.getBoolean(KEY_SHARING, false) ?: false
        set(value) {
            prefs()?.edit { putBoolean(KEY_SHARING, value) }
            if (value) {
                startLoose()
            } else {
                // precise FGS 는 서비스 쪽(ACTION_STOP)에서 종료
                stopLoose()
                synchronized(lock) { buffer.clear() }
            }
        }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** 재부팅·앱 실행 시 복구: 공유 중 + 토큰 + 권한이 모두 있을 때만 loose 시작 */
    fun requestArm(context: Context) {
        init(context)
        if (isSharing && TokenStore.access != null && hasLocationPermission(context)) startLoose()
    }

    @Synchronized
    @SuppressLint("MissingPermission")
    private fun startLoose() {
        val ctx = appContext ?: return
        if (looseCallback != null) return
        if (!isSharing || TokenStore.access == null || !hasLocationPermission(ctx)) return
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, LOOSE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOOSE_INTERVAL_MS / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { bufferAndUpload(it, "COARSE") }
            }
        }
        try {
            LocationServices.getFusedLocationProviderClient(ctx)
                .requestLocationUpdates(request, callback, Looper.getMainLooper())
            looseCallback = callback
        } catch (_: SecurityException) {
            // 권한 회수 — 다음 requestArm 에서 재시도
        }
    }

    @Synchronized
    private fun stopLoose() {
        val ctx = appContext ?: return
        val cb = looseCallback ?: return
        looseCallback = null
        LocationServices.getFusedLocationProviderClient(ctx).removeLocationUpdates(cb)
    }

    /** loose / precise 공용: 버퍼에 쌓고 백그라운드에서 즉시 flush. 조건 미충족 시 no-op */
    fun bufferAndUpload(location: Location, source: String) {
        val ctx = appContext ?: return
        if (!isSharing || !hasLocationPermission(ctx) || TokenStore.access == null) return
        val point = PointDTO(
            lat = location.latitude,
            lng = location.longitude,
            accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            capturedAt = iso(location.time),
            source = source,
        )
        synchronized(lock) {
            buffer.addLast(point)
            while (buffer.size > MAX_BUFFER) buffer.removeFirst() // 가장 오래된 것부터 버림
        }
        executor.execute { flush() }
    }

    /** 단일 스레드 executor 에서만 실행 → flush 끼리 직렬. 실패 시 버퍼에 되돌려 다음 flush 에서 재시도 */
    private fun flush() {
        val batch: List<PointDTO>
        synchronized(lock) {
            if (buffer.isEmpty()) return
            batch = buffer.toList().also { buffer.clear() }
        }
        val ok = try { LocationApi.uploadPoints(batch) } catch (_: Exception) { false }
        if (!ok) {
            synchronized(lock) {
                batch.asReversed().forEach { buffer.addFirst(it) }
                while (buffer.size > MAX_BUFFER) buffer.removeFirst()
            }
        }
    }

    private fun iso(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))
}
