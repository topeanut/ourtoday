package app.ourtoday

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
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
import androidx.core.content.ContextCompat
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

    /** 웹뷰 geolocation(기록에 장소 첨부 "현재 위치") 가 권한을 기다릴 때 보류해 둔 응답 */
    private var pendingGeolocationOrigin: String? = null
    private var pendingGeolocationCallback: GeolocationPermissions.Callback? = null

    /** 위치 런타임 권한 요청 결과 → 보류해 둔 geolocation 콜백에 허용/거부 전달 */
    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingGeolocationCallback?.invoke(pendingGeolocationOrigin, granted, false)
            pendingGeolocationOrigin = null
            pendingGeolocationCallback = null
        }

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

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(intent?.data?.takeIf { isAppUrl(it) }?.toString() ?: BuildConfig.START_URL)
        }
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
        intent.data?.takeIf { isAppUrl(it) }?.let { webView.loadUrl(it.toString()) }
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
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        webView.destroy()
        super.onDestroy()
    }

    private fun isAppUrl(uri: Uri) = NavigationPolicy.decide(uri) == NavigationPolicy.Decision.IN_APP

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

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

        /**
         * 웹(LocationPicker)에서 navigator.geolocation 호출 시.
         * 우리 앱 도메인에서만 허용하고, 앱이 위치 권한을 쥐고 있으면 바로 허용,
         * 없으면 런타임 권한을 요청한 뒤 결과에 따라 허용/거부한다. (보존하지 않음 = retain false)
         */
        override fun onGeolocationPermissionsShowPrompt(
            origin: String?,
            callback: GeolocationPermissions.Callback?
        ) {
            if (callback == null) return
            val host = origin?.let { Uri.parse(it).host?.lowercase() }
            if (host == null || !isAppUrl(Uri.parse(origin))) {
                // 우리 도메인이 아니면 거부
                callback.invoke(origin, false, false)
                return
            }
            if (hasLocationPermission()) {
                callback.invoke(origin, true, false)
                return
            }
            // 런타임 권한 요청 후 결과에서 응답 (동시 요청은 마지막 것으로 대체)
            pendingGeolocationCallback?.invoke(pendingGeolocationOrigin, false, false)
            pendingGeolocationOrigin = origin
            pendingGeolocationCallback = callback
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

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
