import Foundation
import Security

/// 위치 업로드용 JWT 를 Keychain 에 보관. 토큰 값은 로그에 남기지 않는다
final class TokenStore {

    static let shared = TokenStore()

    private static let service = "com.ourtoday.auth"
    private static let accessAccount = "access"
    private static let refreshAccount = "refresh"

    private init() {}

    var access: String? { read(Self.accessAccount) }
    var refresh: String? { read(Self.refreshAccount) }

    /// 기존 값을 덮어쓴다
    func save(access: String, refresh: String) {
        write(access, account: Self.accessAccount)
        write(refresh, account: Self.refreshAccount)
    }

    /// 로그아웃 시 호출
    func clear() {
        delete(Self.accessAccount)
        delete(Self.refreshAccount)
    }

    private func baseQuery(_ account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.service,
            kSecAttrAccount as String: account,
        ]
    }

    private func read(_ account: String) -> String? {
        var query = baseQuery(account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func write(_ value: String, account: String) {
        delete(account)
        var query = baseQuery(account)
        query[kSecValueData as String] = Data(value.utf8)
        // 백그라운드(잠금 상태) 업로드에서도 읽을 수 있어야 함. 기기 밖으로는 안 나감
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(query as CFDictionary, nil)
    }

    private func delete(_ account: String) {
        SecItemDelete(baseQuery(account) as CFDictionary)
    }
}
