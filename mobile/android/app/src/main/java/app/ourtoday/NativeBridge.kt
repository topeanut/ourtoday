package app.ourtoday

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * 웹 → 네이티브 브리지. 웹에서 window.OurTodayNative.postMessage(JSON.stringify({type, ...})) 로 호출
 *
 * JavascriptInterface 는 백그라운드 스레드에서 불리므로 UI 작업은 메인 스레드로 넘김
 */
class NativeBridge(private val handler: Handler) {

    interface Handler {
        fun onShare(title: String?, text: String?, url: String)
        fun onOpenExternal(url: String)
        fun onHaptic()
    }

    private val mainHandler = android.os.Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun postMessage(json: String) {
        val message = runCatching { JSONObject(json) }.getOrNull() ?: return
        mainHandler.post {
            when (message.optString("type")) {
                "share" -> {
                    val url = message.optString("url")
                    if (url.isNotEmpty()) {
                        handler.onShare(message.optStringOrNull("title"), message.optStringOrNull("text"), url)
                    }
                }
                "openExternal" -> {
                    val url = message.optString("url")
                    if (url.isNotEmpty()) handler.onOpenExternal(url)
                }
                "haptic" -> handler.onHaptic()
            }
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).ifEmpty { null } else null

    companion object {
        const val NAME = "OurTodayNative"
    }
}
