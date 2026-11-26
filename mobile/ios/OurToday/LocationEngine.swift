import CoreLocation
import Foundation

/// 위치 공유 엔진. 느슨(significant-change) 상시 + 정밀(startUpdatingLocation) 3분.
/// 공유 OFF / 권한 없음 / 토큰 없음이면 수집·업로드 모두 하지 않는다. 모든 콜백은 메인 스레드
final class LocationEngine: NSObject, CLLocationManagerDelegate {

    static let shared = LocationEngine()

    /// 심사 반려 시 true 로 바꾸면 정밀 수집 중 파란 바가 표시됨
    static var showBlueBar = false

    private static let flushCount = 20
    private static let flushInterval: TimeInterval = 30
    private static let maxBuffer = 300

    private let manager = CLLocationManager()
    private var buffer: [PointDTO] = []
    private var uploading = false
    private var preciseTimer: Timer?
    private var flushTimer: Timer?
    private(set) var isPrecise = false

    var authStatus: CLAuthorizationStatus { manager.authorizationStatus }

    private var isAuthorized: Bool {
        authStatus == .authorizedAlways || authStatus == .authorizedWhenInUse
    }

    var isSharing = false {
        didSet {
            guard isSharing != oldValue else { return }
            if isSharing {
                startLoose()
            } else {
                stopPrecise()
                stopLoose()
                buffer.removeAll()
            }
        }
    }

    private override init() {
        super.init()
        manager.delegate = self
        manager.pausesLocationUpdatesAutomatically = false
    }

    /// WhenInUse 먼저, 이미 허용돼 있으면 Always 로 업그레이드 요청
    func requestAuthorization() {
        switch authStatus {
        case .notDetermined: manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse: manager.requestAlwaysAuthorization()
        default: break
        }
    }

    // MARK: - 느슨

    private func startLoose() {
        guard isSharing, isAuthorized else { return }
        if CLLocationManager.significantLocationChangeMonitoringAvailable() {
            manager.startMonitoringSignificantLocationChanges()
        }
        startFlushTimer()
    }

    private func stopLoose() {
        manager.stopMonitoringSignificantLocationChanges()
        flushTimer?.invalidate()
        flushTimer = nil
    }

    // MARK: - 정밀

    func startPrecise(seconds: TimeInterval = 180) {
        guard isSharing, isAuthorized else { return }
        if !isPrecise {
            isPrecise = true
            // Always 일 때만 백그라운드 갱신 허용 (아니면 크래시 위험)
            if authStatus == .authorizedAlways {
                manager.allowsBackgroundLocationUpdates = true
                manager.showsBackgroundLocationIndicator = Self.showBlueBar
            }
            manager.desiredAccuracy = kCLLocationAccuracyBest
            manager.startUpdatingLocation()
        }
        preciseTimer?.invalidate()
        preciseTimer = Timer.scheduledTimer(withTimeInterval: seconds, repeats: false) { [weak self] _ in
            self?.stopPrecise()
        }
    }

    func stopPrecise() {
        preciseTimer?.invalidate()
        preciseTimer = nil
        guard isPrecise else { return }
        isPrecise = false
        manager.stopUpdatingLocation()
        if authStatus == .authorizedAlways {
            manager.allowsBackgroundLocationUpdates = false
        }
        flush()
    }

    // MARK: - CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard isSharing, isAuthorized, TokenStore.shared.access != nil else { return }
        for loc in locations {
            buffer.append(PointDTO(
                lat: loc.coordinate.latitude,
                lng: loc.coordinate.longitude,
                accuracy: loc.horizontalAccuracy >= 0 ? loc.horizontalAccuracy : nil,
                capturedAt: LocationAPI.isoString(loc.timestamp),
                source: isPrecise ? "PRECISE" : "COARSE"))
        }
        if buffer.count > Self.maxBuffer { buffer.removeFirst(buffer.count - Self.maxBuffer) }
        if buffer.count >= Self.flushCount { flush() }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if isSharing, isAuthorized {
            startLoose()
        } else {
            // 권한 회수 시 전부 중단
            stopPrecise()
            stopLoose()
            buffer.removeAll()
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // 일시적 실패는 무시 (다음 업데이트를 기다림)
    }

    // MARK: - 업로드

    private func startFlushTimer() {
        guard flushTimer == nil else { return }
        flushTimer = Timer.scheduledTimer(withTimeInterval: Self.flushInterval, repeats: true) { [weak self] _ in
            self?.flush()
        }
    }

    private func flush() {
        guard isSharing, isAuthorized, TokenStore.shared.access != nil,
              !uploading, !buffer.isEmpty else { return }
        let batch = buffer
        uploading = true
        LocationAPI.shared.uploadPoints(batch) { [weak self] result in
            guard let self = self else { return }
            self.uploading = false
            // 실패하면 버퍼에 남겨 다음 flush 에서 재시도. 공유가 꺼졌으면 이미 비워짐
            if case .success = result, self.isSharing {
                self.buffer.removeFirst(min(batch.count, self.buffer.count))
            }
        }
    }
}
