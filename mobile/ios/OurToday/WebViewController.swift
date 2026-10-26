import UIKit
import WebKit

final class WebViewController: UIViewController {

    private var webView: WKWebView!
    private let refreshControl = UIRefreshControl()
    private let errorView = ErrorView()

    override var preferredStatusBarStyle: UIStatusBarStyle { .darkContent }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = AppConfig.background
        setUpWebView()
        setUpErrorView()
        webView.load(URLRequest(url: AppConfig.startURL))
    }

    private func setUpWebView() {
        let contentController = WKUserContentController()
        // 핸들러가 컨트롤러를 강하게 잡지 않도록 프록시 사용
        contentController.add(WeakScriptMessageHandler(self), name: "ourtoday")

        let config = WKWebViewConfiguration()
        config.userContentController = contentController
        config.websiteDataStore = .default() // localStorage(JWT)·쿠키 영구 저장
        config.allowsInlineMediaPlayback = true
        config.applicationNameForUserAgent = AppConfig.userAgentSuffix
        config.defaultWebpagePreferences.allowsContentJavaScript = true

        webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.allowsBackForwardNavigationGestures = true
        webView.isOpaque = false
        webView.backgroundColor = AppConfig.background
        webView.scrollView.backgroundColor = AppConfig.background
        // 웹이 env(safe-area-inset-*) 로 직접 여백 처리 (viewport-fit=cover)
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        #if DEBUG
        if #available(iOS 16.4, *) {
            webView.isInspectable = true
        }
        #endif

        refreshControl.tintColor = AppConfig.brand
        refreshControl.addTarget(self, action: #selector(reload), for: .valueChanged)
        webView.scrollView.refreshControl = refreshControl

        webView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(webView)
        NSLayoutConstraint.activate([
            webView.topAnchor.constraint(equalTo: view.topAnchor),
            webView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            webView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
        ])
    }

    private func setUpErrorView() {
        errorView.isHidden = true
        errorView.onRetry = { [weak self] in
            guard let self else { return }
            self.errorView.isHidden = true
            if self.webView.url == nil {
                self.webView.load(URLRequest(url: AppConfig.startURL))
            } else {
                self.webView.reload()
            }
        }
        errorView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(errorView)
        NSLayoutConstraint.activate([
            errorView.topAnchor.constraint(equalTo: view.topAnchor),
            errorView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            errorView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            errorView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
        ])
    }

    @objc private func reload() {
        webView.reload()
    }

    private func showError() {
        refreshControl.endRefreshing()
        errorView.isHidden = false
    }

    // MARK: - 브리지 동작

    fileprivate func handle(message body: Any) {
        guard let dict = body as? [String: Any], let type = dict["type"] as? String else { return }
        switch type {
        case "share":
            guard let urlString = dict["url"] as? String, let url = URL(string: urlString) else { return }
            share(title: dict["title"] as? String, text: dict["text"] as? String, url: url)
        case "openExternal":
            guard let urlString = dict["url"] as? String, let url = URL(string: urlString) else { return }
            openExternally(url)
        case "haptic":
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        default:
            break
        }
    }

    private func share(title: String?, text: String?, url: URL) {
        var items: [Any] = []
        if let text, !text.isEmpty { items.append(text) }
        items.append(url)
        let activity = UIActivityViewController(activityItems: items, applicationActivities: nil)
        if let title { activity.setValue(title, forKey: "subject") }
        // iPad 는 popover 필수 → 화면 아래 가운데에 화살표 없이
        if let popover = activity.popoverPresentationController {
            popover.sourceView = view
            popover.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.maxY - 80, width: 0, height: 0)
            popover.permittedArrowDirections = []
        }
        present(activity, animated: true)
    }

    private func openExternally(_ url: URL) {
        UIApplication.shared.open(url, options: [:], completionHandler: nil)
    }
}

// MARK: - WKNavigationDelegate

extension WebViewController: WKNavigationDelegate {

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = navigationAction.request.url else {
            decisionHandler(.cancel)
            return
        }
        // iframe 등 하위 프레임은 그대로 둠
        let isMainFrame = navigationAction.targetFrame?.isMainFrame ?? true
        guard isMainFrame else {
            decisionHandler(.allow)
            return
        }
        switch NavigationPolicy.decide(url) {
        case .inApp:
            decisionHandler(.allow)
        case .externalBrowser, .osHandoff:
            // 카카오톡 앱 로그인(kakaokompassauth:// 등) 포함. 실패해도 무시
            openExternally(url)
            decisionHandler(.cancel)
        }
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        errorView.isHidden = true
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        refreshControl.endRefreshing()
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!,
                 withError error: Error) {
        handleLoadError(error)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        handleLoadError(error)
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        webView.reload()
    }

    private func handleLoadError(_ error: Error) {
        let nsError = error as NSError
        // 취소(-999)·정책으로 막은 이동(WebKit 102)은 오류 화면 띄우지 않음
        if nsError.domain == NSURLErrorDomain && nsError.code == NSURLErrorCancelled { return }
        if nsError.domain == "WebKitErrorDomain" && nsError.code == 102 { return }
        showError()
    }
}

// MARK: - WKUIDelegate

extension WebViewController: WKUIDelegate {

    /// target=_blank / window.open → 앱 도메인이면 같은 WebView, 아니면 외부
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                 for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if let url = navigationAction.request.url {
            if NavigationPolicy.decide(url) == .inApp {
                webView.load(navigationAction.request)
            } else {
                openExternally(url)
            }
        }
        return nil
    }

    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "확인", style: .default) { _ in completionHandler() })
        present(alert, animated: true)
    }

    func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) {
        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "취소", style: .cancel) { _ in completionHandler(false) })
        alert.addAction(UIAlertAction(title: "확인", style: .default) { _ in completionHandler(true) })
        present(alert, animated: true)
    }
}

// MARK: - 브리지

extension WebViewController: WKScriptMessageHandler {

    func userContentController(_ userContentController: WKUserContentController,
                               didReceive message: WKScriptMessage) {
        guard message.name == "ourtoday" else { return }
        handle(message: message.body)
    }
}

/// WKUserContentController 가 핸들러를 강하게 잡아 생기는 순환 참조 방지
private final class WeakScriptMessageHandler: NSObject, WKScriptMessageHandler {

    private weak var target: WKScriptMessageHandler?

    init(_ target: WKScriptMessageHandler) {
        self.target = target
    }

    func userContentController(_ userContentController: WKUserContentController,
                               didReceive message: WKScriptMessage) {
        target?.userContentController(userContentController, didReceive: message)
    }
}
