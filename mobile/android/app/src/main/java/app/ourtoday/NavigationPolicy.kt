package app.ourtoday

import android.net.Uri

/** 메인 프레임 이동을 WebView 안에서 열지, 밖(브라우저/다른 앱)으로 넘길지 결정 */
object NavigationPolicy {

    private val appHost: String = Uri.parse(BuildConfig.START_URL).host.orEmpty()

    /** 카카오 로그인 화면들 (accounts.kakao.com, kauth.kakao.com, logins.daum.net 등) */
    private val loginHostSuffixes = listOf("kakao.com", "kakao.co.kr", "daum.net")

    enum class Decision { IN_APP, EXTERNAL_BROWSER, OS_HANDOFF }

    fun decide(uri: Uri): Decision {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return Decision.OS_HANDOFF
        }
        val host = uri.host?.lowercase().orEmpty()
        if (host == appHost || host == "localhost" || host == "10.0.2.2") {
            return Decision.IN_APP
        }
        if (loginHostSuffixes.any { host == it || host.endsWith(".$it") }) {
            return Decision.IN_APP
        }
        return Decision.EXTERNAL_BROWSER
    }
}
