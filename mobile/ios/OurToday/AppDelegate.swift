import FirebaseCore
import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        FirebaseApp.configure()
        PushManager.shared.start()
        // 위치 이벤트(significant-change)로 재실행돼도 엔진 init 의 재무장이 돌도록 매 실행마다 참조
        _ = LocationEngine.shared
        return true
    }

    func application(_ application: UIApplication,
                     configurationForConnecting connectingSceneSession: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
    }

    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        PushManager.shared.setAPNsToken(deviceToken)
    }

    func application(_ application: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        PushManager.shared.apnsRegistrationFailed(error)
    }

    /// 무음(content-available) 푸시: 위치 정밀 모드 켜기/끄기. 그 외 타입은 건드리지 않음
    func application(_ application: UIApplication,
                     didReceiveRemoteNotification userInfo: [AnyHashable: Any],
                     fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        switch userInfo["type"] as? String {
        case "location_precise_on":
            LocationEngine.shared.startPrecise()
            completionHandler(.newData)
        case "location_precise_off":
            LocationEngine.shared.stopPrecise()
            completionHandler(.newData)
        default:
            completionHandler(.noData)
        }
    }
}
