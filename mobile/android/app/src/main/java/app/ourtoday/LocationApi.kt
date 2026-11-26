package app.ourtoday

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

data class PointDTO(
    val lat: Double,
    val lng: Double,
    val accuracy: Double?,
    /** ISO8601 (UTC) */
    val capturedAt: String,
    /** "COARSE" | "PRECISE" */
    val source: String,
)

/** 위치 업로드 + 토큰 갱신. 모두 동기 호출이므로 반드시 백그라운드 스레드에서 사용 */
object LocationApi {

    private const val BASE = "https://ourtoday.vercel.app/api/v1"
    private const val TIMEOUT_MS = 10_000

    fun uploadPoints(points: List<PointDTO>): Boolean {
        if (points.isEmpty()) return true
        val body = JSONObject().put("points", JSONArray().also { arr ->
            points.forEach { p ->
                arr.put(JSONObject().apply {
                    put("lat", p.lat)
                    put("lng", p.lng)
                    p.accuracy?.let { put("accuracy", it) }
                    put("capturedAt", p.capturedAt)
                    put("source", p.source)
                })
            }
        }).toString()

        val first = post("/location/points", body, TokenStore.access ?: return false)
        if (first in 200..299) return true
        if (first != 401) return false
        // 액세스 토큰 만료 → 갱신 후 한 번만 재시도
        if (!refresh()) return false
        val retry = post("/location/points", body, TokenStore.access ?: return false)
        return retry in 200..299
    }

    @Synchronized
    fun refresh(): Boolean {
        val refreshToken = TokenStore.refresh ?: return false
        val (code, text) = request("/auth/refresh", JSONObject().put("refreshToken", refreshToken).toString(), null)
        if (code !in 200..299 || text == null) return false
        return try {
            val json = JSONObject(text)
            val a = json.getString("accessToken")
            val r = json.getString("refreshToken")
            if (a.isEmpty() || r.isEmpty()) return false
            TokenStore.save(a, r)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun post(path: String, body: String, bearer: String): Int = request(path, body, bearer).first

    /** (상태코드, 응답 본문). 네트워크 오류는 (-1, null) */
    private fun request(path: String, body: String, bearer: String?): Pair<Int, String?> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            code to text
        } catch (_: Exception) {
            -1 to null
        } finally {
            conn?.disconnect()
        }
    }
}
