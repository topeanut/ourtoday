package app.ourtoday

import android.net.Uri

/** 메인 프레임 이동을 WebView 안에서 열지, 밖(브라우저/다른 앱)으로 넘길지 결정 */
object NavigationPolicy {

    private val appHost: String = Uri.parse(BuildConfig.START_URL).host.orEmpty()

    /** 카카오 로그인 화면만 (카페·블로그 등 사용자 콘텐츠 도메인은 제외) */
    private val loginHosts = setOf(
        "kauth.kakao.com", "accounts.kakao.com", "logins.kakao.com",
        "accounts.daum.net", "logins.daum.net",
    )

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
        if (host in loginHosts) {
            return Decision.IN_APP
        }
        return Decision.EXTERNAL_BROWSER
    }

    /** 브리지를 쓸 수 있는 페이지인지 (우리 웹만) */
    fun isAppPage(url: String?): Boolean {
        val host = url?.let { Uri.parse(it).host?.lowercase() } ?: return false
        return host == appHost || (BuildConfig.DEBUG && (host == "localhost" || host == "10.0.2.2"))
    }
}
