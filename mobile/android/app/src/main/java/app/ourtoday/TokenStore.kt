package app.ourtoday

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 네이티브 업로드용 액세스/리프레시 토큰 보관 (EncryptedSharedPreferences). 값은 로그에 남기지 않음.
 * 사용 전 init(context) 필요 (LocationEngine.init 이 호출)
 */
object TokenStore {

    private const val FILE = "location_tokens"
    private const val KEY_ACCESS = "access"
    private const val KEY_REFRESH = "refresh"

    @Volatile private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        prefs = try {
            open(app)
        } catch (_: Exception) {
            // 키스토어 손상(드묾): 암호화 파일을 지우고 한 번 재시도. 그래도 실패하면 보관 불가(null) → 토큰 없음으로 동작
            try {
                app.deleteSharedPreferences(FILE)
                open(app)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun open(app: Context): SharedPreferences {
        val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            app, FILE, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val access: String? get() = read(KEY_ACCESS)
    val refresh: String? get() = read(KEY_REFRESH)

    private fun read(key: String): String? =
        try { prefs?.getString(key, null)?.takeIf { it.isNotEmpty() } } catch (_: Exception) { null }

    fun save(access: String, refresh: String) {
        try {
            prefs?.edit { putString(KEY_ACCESS, access); putString(KEY_REFRESH, refresh) }
        } catch (_: Exception) {
        }
    }

    fun clear() {
        try { prefs?.edit { clear() } } catch (_: Exception) { }
    }
}
