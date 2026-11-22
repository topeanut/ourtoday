import FirebaseMessaging
import UIKit
import UserNotifications

/// 웹에 전달하는 푸시 상태 (ourtoday:push 이벤트의 detail)
struct PushState {
    let permission: String // "granted" | "denied" | "default"
    let token: String?

    var detail: [String: Any] {
        ["permission": permission, "token": token ?? NSNull(), "platform": "ios"]
    }
}

/// FCM 구성·권한·토큰 관리와 알림 탭 경로 전달. 모든 콜백은 메인 스레드에서 호출됨
final class PushManager: NSObject {

    static let shared = PushManager()

    /// 권한 허용 직후 FCM 토큰을 기다리는 최대 시간
    private static let tokenWaitSeconds: TimeInterval = 10

    /// FCM 토큰이 새로 생기거나 바뀔 때 (웹뷰가 살아 있으면 웹에 알림)
    var onTokenChange: ((PushState) -> Void)?

    /// 알림을 탭해 열 경로. 설정되는 순간 대기 중이던 경로도 바로 전달
    var onOpenPath: ((String) -> Void)? {
        didSet { flushPendingPath() }
    }

    private var fcmToken: String?
    private var pendingPath: String?
    private var tokenWaiters: [(String?) -> Void] = []

    /// didFinishLaunching 안에서 FirebaseApp.configure() 다음에 호출
    /// (콜드 스타트 탭을 받으려면 실행이 끝나기 전에 delegate 를 지정해야 함)
    func start() {
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        // 이미 허용된 기기는 실행할 때마다 APNs 등록 (토큰 갱신)
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            guard Self.isGranted(settings.authorizationStatus) else { return }
            DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() }
        }
    }

    func setAPNsToken(_ deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    // MARK: - 웹 요청

    /// pushStatus: 권한을 묻지 않고 현재 상태만
    func currentState(completion: @escaping (PushState) -> Void) {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            let permission = Self.permission(for: settings.authorizationStatus)
            DispatchQueue.main.async {
                guard permission == "granted" else {
                    completion(PushState(permission: permission, token: nil))
                    return
                }
                self.fetchToken { completion(PushState(permission: permission, token: $0)) }
            }
        }
    }

    /// pushPermission: 아직 묻지 않았으면 시스템 권한 대화상자. 허용되면 토큰이 나올 때까지 기다렸다가 응답
    func requestPermission(completion: @escaping (PushState) -> Void) {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in
            center.getNotificationSettings { settings in
                let permission = Self.permission(for: settings.authorizationStatus)
                DispatchQueue.main.async {
                    guard permission == "granted" else {
                        completion(PushState(permission: permission, token: nil))
                        return
                    }
                    UIApplication.shared.registerForRemoteNotifications()
                    self.waitForToken { completion(PushState(permission: permission, token: $0)) }
                }
            }
        }
    }

    func openSettings() {
        let raw: String
        if #available(iOS 16.0, *) {
            raw = UIApplication.openNotificationSettingsURLString
        } else {
            raw = UIApplication.openSettingsURLString
        }
        guard let url = URL(string: raw) else { return }
        UIApplication.shared.open(url, options: [:], completionHandler: nil)
    }

    // MARK: - 토큰

    /// 이미 있으면 바로, 없으면 FCM 에 한 번 물어봄 (APNs 토큰 전이면 nil → 나중에 onTokenChange 로 전달)
    private func fetchToken(completion: @escaping (String?) -> Void) {
        if let fcmToken {
            completion(fcmToken)
            return
        }
        Messaging.messaging().token { token, _ in
            DispatchQueue.main.async {
                if let token, !token.isEmpty { self.fcmToken = token }
                completion(self.fcmToken)
            }
        }
    }

    /// 권한 허용 직후: APNs 등록 → FCM 토큰 발급까지 시간이 걸리므로 delegate 콜백을 기다림
    private func waitForToken(completion: @escaping (String?) -> Void) {
        if let fcmToken {
            completion(fcmToken)
            return
        }
        var finished = false
        let finish: (String?) -> Void = { token in
            guard !finished else { return }
            finished = true
            completion(token)
        }
        tokenWaiters.append(finish)
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.tokenWaitSeconds) { finish(self.fcmToken) }
    }

    private func tokenDidChange(_ token: String) {
        let changed = token != fcmToken
        fcmToken = token
        let waiters = tokenWaiters
        tokenWaiters.removeAll()
        waiters.forEach { $0(token) }
        if changed {
            onTokenChange?(PushState(permission: "granted", token: token))
        }
    }

    // MARK: - 알림 탭

    private func openPath(from userInfo: [AnyHashable: Any]) {
        // 우리 앱 안의 절대 경로만 ("//host" 같은 프로토콜 상대 URL 차단)
        guard let path = userInfo["path"] as? String,
              path.hasPrefix("/"), !path.hasPrefix("//") else { return }
        pendingPath = path
        flushPendingPath()
    }

    private func flushPendingPath() {
        guard let path = pendingPath, let onOpenPath else { return }
        pendingPath = nil
        onOpenPath(path)
    }

    // MARK: - 권한 매핑

    private static func permission(for status: UNAuthorizationStatus) -> String {
        if isGranted(status) { return "granted" }
        return status == .denied ? "denied" : "default"
    }

    private static func isGranted(_ status: UNAuthorizationStatus) -> Bool {
        switch status {
        case .authorized, .provisional, .ephemeral: return true
        case .denied, .notDetermined: return false
        @unknown default: return false
        }
    }
}

// MARK: - MessagingDelegate

extension PushManager: MessagingDelegate {

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken, !fcmToken.isEmpty else { return }
        DispatchQueue.main.async { self.tokenDidChange(fcmToken) }
    }
}

// MARK: - UNUserNotificationCenterDelegate

extension PushManager: UNUserNotificationCenterDelegate {

    /// 앱을 보고 있을 때도 배너·소리로 표시
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let userInfo = response.notification.request.content.userInfo
        DispatchQueue.main.async {
            self.openPath(from: userInfo)
            completionHandler()
        }
    }
}
