import Foundation

/// 서버로 올리는 위치 포인트 한 건
struct PointDTO: Encodable {
    let lat: Double
    let lng: Double
    let accuracy: Double?
    let capturedAt: String // ISO8601
    let source: String // "COARSE" | "PRECISE"
}

enum LocationAPIError: Error {
    case noToken
    case badStatus(Int)
    case network
}

/// 위치 업로드 + 토큰 재발급. 모든 completion 은 메인 스레드에서 호출됨
final class LocationAPI {

    static let shared = LocationAPI()

    /// 웹과 같은 출처 경유 (웹이 /api/v1 을 백엔드로 프록시)
    private let baseURL = AppConfig.startURL.appendingPathComponent("api/v1")
    private let session = URLSession(configuration: .default)

    private init() {}

    static func isoString(_ date: Date) -> String {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f.string(from: date)
    }

    /// 401 이면 refresh 후 딱 1회만 재시도. refresh 실패 시 바로 실패 (루프 없음)
    func uploadPoints(_ points: [PointDTO], completion: @escaping (Result<Void, Error>) -> Void) {
        send(points, allowRetry: true, completion: completion)
    }

    private func send(_ points: [PointDTO], allowRetry: Bool,
                      completion: @escaping (Result<Void, Error>) -> Void) {
        guard let token = TokenStore.shared.access else {
            DispatchQueue.main.async { completion(.failure(LocationAPIError.noToken)) }
            return
        }
        struct Body: Encodable { let points: [PointDTO] }
        var req = URLRequest(url: baseURL.appendingPathComponent("location/points"))
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        req.timeoutInterval = 20
        do {
            req.httpBody = try JSONEncoder().encode(Body(points: points))
        } catch {
            DispatchQueue.main.async { completion(.failure(error)) }
            return
        }
        session.dataTask(with: req) { [weak self] _, response, error in
            DispatchQueue.main.async {
                if let error = error { return completion(.failure(error)) }
                let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                if (200..<300).contains(status) { return completion(.success(())) }
                if status == 401, allowRetry, let self = self {
                    self.refresh { ok in
                        if ok {
                            self.send(points, allowRetry: false, completion: completion)
                        } else {
                            completion(.failure(LocationAPIError.badStatus(401)))
                        }
                    }
                    return
                }
                completion(.failure(LocationAPIError.badStatus(status)))
            }
        }.resume()
    }

    /// POST auth/refresh { "refreshToken": ... } → { accessToken, refreshToken } (웹 api.ts 와 동일)
    func refresh(completion: @escaping (Bool) -> Void) {
        guard let refreshToken = TokenStore.shared.refresh else {
            DispatchQueue.main.async { completion(false) }
            return
        }
        var req = URLRequest(url: baseURL.appendingPathComponent("auth/refresh"))
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.timeoutInterval = 20
        req.httpBody = try? JSONSerialization.data(withJSONObject: ["refreshToken": refreshToken])
        session.dataTask(with: req) { data, response, _ in
            var ok = false
            if let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
               let data = data,
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let newAccess = json["accessToken"] as? String {
                let newRefresh = (json["refreshToken"] as? String) ?? refreshToken
                TokenStore.shared.save(access: newAccess, refresh: newRefresh)
                ok = true
            }
            DispatchQueue.main.async { completion(ok) }
        }.resume()
    }
}
