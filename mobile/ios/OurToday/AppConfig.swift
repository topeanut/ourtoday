import UIKit

enum AppConfig {

    /// 시작 URL — project.yml 의 OURTODAY_START_URL → Info.plist OurTodayStartURL
    static let startURL: URL = {
        let raw = Bundle.main.object(forInfoDictionaryKey: "OurTodayStartURL") as? String
        return raw.flatMap(URL.init(string:)) ?? URL(string: "https://ourtoday.vercel.app/")!
    }()

    /// 기본 UA 끝에 붙는 값. 웹은 /OurTodayApp/ 로 앱 여부 판단
    static let userAgentSuffix = "Mobile/15E148 OurTodayApp/1.0 (ios)"

    /// WebView 안에서 열 카카오 로그인 화면 (카페·블로그 등 사용자 콘텐츠 도메인은 제외)
    static let loginHosts: Set<String> = [
        "kauth.kakao.com", "accounts.kakao.com", "logins.kakao.com",
        "accounts.daum.net", "logins.daum.net",
    ]

    static let brand = UIColor(red: 1.0, green: 0x7A / 255.0, blue: 0x8A / 255.0, alpha: 1)
    static let background = UIColor(red: 1.0, green: 0xF8 / 255.0, blue: 0xF5 / 255.0, alpha: 1)
    static let textPrimary = UIColor(red: 0x3A / 255.0, green: 0x2E / 255.0, blue: 0x2C / 255.0, alpha: 1)
    static let textSecondary = UIColor(red: 0x8A / 255.0, green: 0x7A / 255.0, blue: 0x76 / 255.0, alpha: 1)
}

enum NavigationDecision {
    case inApp
    case externalBrowser
    case osHandoff
}

enum NavigationPolicy {

    static func decide(_ url: URL) -> NavigationDecision {
        let scheme = url.scheme?.lowercased() ?? ""
        if scheme == "about" || scheme == "blob" || scheme == "data" {
            return .inApp
        }
        guard scheme == "http" || scheme == "https" else {
            return .osHandoff
        }
        let host = url.host?.lowercased() ?? ""
        if host == AppConfig.startURL.host?.lowercased() || host == "localhost" {
            return .inApp
        }
        if AppConfig.loginHosts.contains(host) {
            return .inApp
        }
        return .externalBrowser
    }
}
