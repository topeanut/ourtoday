package app.ourtoday

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity(), NativeBridge.Handler {

    /** 브리지 호출 출처 확인용 (JavascriptInterface 는 다른 스레드에서 읽음) */
    @Volatile private var currentMainUrl: String? = null

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var errorView: View

    private var fileCallback: ValueCallback<Array<Uri>>? = null

    /** 여러 장 선택 가능한 갤러리 (Android 13+ 는 시스템 포토 피커) */
    private val pickImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        fileCallback?.onReceiveValue(uris.toTypedArray())
        fileCallback = null
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback?.onReceiveValue(uri?.let { arrayOf(it) } ?: emptyArray())
        fileCallback = null
    }

    /** 권한 요청 직전의 shouldShowRequestPermissionRationale (결과가 "결정" 인지 "그냥 닫음" 인지 가르는 데 씀) */
    private var rationaleBeforeRequest = false

    /**
     * Android 13+ 알림 권한. 사용자가 실제로 허용/거부했을 때만 "물어봤음" 을 기록하고 웹에 알림.
     * 뒤로가기·바깥 탭으로 닫으면 기록하지 않아 웹에는 계속 "default"(다시 물어볼 수 있음)로 보임
     */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val rationaleAfter = shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            if (PushBridge.isRealDecision(granted, rationaleBeforeRequest, rationaleAfter)) {
                PushBridge.markAsked(this)
            }
            sendPushState()
        }

    private val onPushTokenChanged: (String) -> Unit = { sendPushState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.web_view)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        errorView = findViewById(R.id.error_view)

        applySystemBarInsets()
        setUpWebView()
        setUpRefresh()
        setUpBack()

        findViewById<Button>(R.id.retry_button).setOnClickListener {
            errorView.visibility = View.GONE
            if (webView.url.isNullOrEmpty()) webView.loadUrl(BuildConfig.START_URL) else webView.reload()
        }

        PushBridge.ensureChannel(this)
        PushBridge.tokenListener = onPushTokenChanged

        // 알림 탭으로 새로 켜진 경우 그 경로 (최근 앱에서 다시 연 경우엔 예전 intent 이므로 무시)
        val fromHistory = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        val pushUrl = if (savedInstanceState == null && !fromHistory) notificationUrl(intent) else null
        if (pushUrl != null) {
            webView.loadUrl(pushUrl)
        } else if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(intent?.data?.takeIf { isAppUrl(it) }?.toString() ?: BuildConfig.START_URL)
        }
    }

    /** 알림 탭 intent extra "path" → 우리 앱 URL (검증 실패 시 null) */
    private fun notificationUrl(intent: Intent?): String? {
        val path = PushBridge.safePath(intent?.getStringExtra(PushBridge.EXTRA_PATH)) ?: return null
        val start = BuildConfig.START_URL.toUri()
        val url = "${start.scheme}://${start.encodedAuthority}$path".toUri()
        return url.takeIf { isAppUrl(it) && it.host == start.host }?.toString()
    }

    /** targetSdk 35 는 Android 15 에서 edge-to-edge 강제 → 시스템 바·키보드 영역만큼 여백 */
    private fun applySystemBarInsets() {
        val root = findViewById<ViewGroup>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWebView() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true // JWT 를 localStorage 에 저장
            databaseEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
            userAgentString = "$userAgentString OurTodayApp/1.0 (android)"
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        webView.addJavascriptInterface(NativeBridge(this) { currentMainUrl }, NativeBridge.NAME)
        webView.webViewClient = AppWebViewClient()
        webView.webChromeClient = AppChromeClient()
    }

    private fun setUpRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.brand)
        swipeRefresh.setOnRefreshListener { webView.reload() }
        // 페이지 맨 위일 때만 당겨서 새로고침 (안쪽 스크롤 가로채지 않게)
        swipeRefresh.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 || webView.canScrollVertically(-1) }
    }

    private fun setUpBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = notificationUrl(intent) ?: intent.data?.takeIf { isAppUrl(it) }?.toString()
        url?.let { webView.loadUrl(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        if (PushBridge.tokenListener === onPushTokenChanged) PushBridge.tokenListener = null
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        webView.destroy()
        super.onDestroy()
    }

    private fun isAppUrl(uri: Uri) = NavigationPolicy.decide(uri) == NavigationPolicy.Decision.IN_APP

    // ===== 브리지 =====

    override fun onShare(title: String?, text: String?, url: String) {
        val body = listOfNotNull(text, url).joinToString("\n")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }
        startActivity(Intent.createChooser(send, title ?: getString(R.string.share_title)))
    }

    override fun onOpenExternal(url: String) {
        openExternally(Uri.parse(url))
    }

    override fun onHaptic() {
        webView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    override fun onPushStatus() {
        sendPushState()
    }

    override fun onPushPermission() {
        if (PushBridge.needsRuntimePermission(this)) {
            rationaleBeforeRequest = shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            sendPushState()
        }
    }

    override fun onOpenNotificationSettings() {
        val settings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(settings)
        } catch (_: ActivityNotFoundException) {
            // 설정 화면 없음 → 무시
        }
    }

    /**
     * 현재 권한·토큰을 웹에 "ourtoday:push" 이벤트로 전달 (우리 웹 페이지일 때만).
     * 허용 상태가 아니면 토큰이 필요 없으니 FCM 조회 없이 바로 응답 (느린 네트워크에서 웹 1.5초 타임아웃 방지)
     */
    private fun sendPushState() {
        val permission = PushBridge.permission(this)
        if (permission != "granted") {
            dispatchPushState(permission, null)
            return
        }
        PushBridge.token(this) { token -> dispatchPushState(PushBridge.permission(this), token) }
    }

    private fun dispatchPushState(permission: String, token: String?) {
        if (isDestroyed || !NavigationPolicy.isAppPage(currentMainUrl)) return
        webView.evaluateJavascript(PushBridge.eventScript(permission, token), null)
    }

    // ===== 외부 이동 =====

    private fun openExternally(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
            // 처리할 앱 없음 → 무시
        }
    }

    /** intent:// (카카오톡 로그인 등). 앱이 없으면 browser_fallback_url → 플레이스토어 순 */
    private fun handleIntentScheme(url: String): Boolean {
        val intent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (_: Exception) {
            return true
        }
        // 다른 앱 컴포넌트를 직접 지정하지 못하게
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        try {
            startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
        }
        val fallback = intent.getStringExtra("browser_fallback_url")
        if (!fallback.isNullOrEmpty()) {
            val fallbackUri = Uri.parse(fallback)
            if (isAppUrl(fallbackUri)) webView.loadUrl(fallback) else openExternally(fallbackUri)
            return true
        }
        intent.`package`?.let { pkg ->
            openExternally(Uri.parse("market://details?id=$pkg"))
        }
        return true
    }

    private inner class AppWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val uri = request.url
            return when (NavigationPolicy.decide(uri)) {
                NavigationPolicy.Decision.IN_APP -> false
                NavigationPolicy.Decision.EXTERNAL_BROWSER -> {
                    openExternally(uri)
                    true
                }
                NavigationPolicy.Decision.OS_HANDOFF -> {
                    if (uri.scheme.equals("intent", ignoreCase = true)) {
                        handleIntentScheme(uri.toString())
                    } else {
                        openExternally(uri)
                        true
                    }
                }
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            // SPA 내부 이동(pushState)도 반영
            currentMainUrl = url
            super.doUpdateVisitedHistory(view, url, isReload)
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            currentMainUrl = url
            errorView.visibility = View.GONE
        }

        override fun onPageFinished(view: WebView, url: String?) {
            swipeRefresh.isRefreshing = false
            CookieManager.getInstance().flush()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                swipeRefresh.isRefreshing = false
                errorView.visibility = View.VISIBLE
            }
        }
    }

    private inner class AppChromeClient : WebChromeClient() {

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = filePathCallback
            val mime = fileChooserParams.acceptTypes.firstOrNull { it.isNotBlank() } ?: "image/*"
            return try {
                if (fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    pickImages.launch(mime)
                } else {
                    pickImage.launch(mime)
                }
                true
            } catch (_: ActivityNotFoundException) {
                fileCallback = null
                false
            }
        }
    }
}
