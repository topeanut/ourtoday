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
    /// 마지막 업로드 실패 시각 (실패 직후 매 업데이트마다 재시도하는 폭주 방지)
    private var lastUploadFailureAt: Date?
    private static let retryBackoff: TimeInterval = 30
    private static let sharingKey = "ourtoday.location.sharing"
    private(set) var isPrecise = false
    /// 직전 권한 상태 (WhenInUse 로 '바뀔 때'만 Always 승격을 요청하기 위함)
    private var lastAuthStatus: CLAuthorizationStatus?

    var authStatus: CLAuthorizationStatus { manager.authorizationStatus }

    private var isAuthorized: Bool {
        authStatus == .authorizedAlways || authStatus == .authorizedWhenInUse
    }

    var isSharing = false {
        didSet {
            guard isSharing != oldValue else { return }
            // 재실행(significant-change 로 깨어남) 때 다시 무장하도록 저장
            UserDefaults.standard.set(isSharing, forKey: Self.sharingKey)
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
        // 재실행 시 저장된 공유 상태 복원 (권한·토큰이 있을 때만)
        if UserDefaults.standard.bool(forKey: Self.sharingKey), TokenStore.shared.access != nil {
            isSharing = true
        }
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
        guard isSharing, isAuthorized, TokenStore.shared.access != nil else { return }
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
        manager.allowsBackgroundLocationUpdates = false
        flush(force: true)
    }

    // MARK: - CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard isSharing, isAuthorized, TokenStore.shared.access != nil else { return }
        let wasPrecise = isPrecise
        for loc in locations {
            buffer.append(PointDTO(
                lat: loc.coordinate.latitude,
                lng: loc.coordinate.longitude,
                accuracy: loc.horizontalAccuracy >= 0 ? loc.horizontalAccuracy : nil,
                capturedAt: LocationAPI.isoString(loc.timestamp),
                source: isPrecise ? "PRECISE" : "COARSE"))
        }
        if buffer.count > Self.maxBuffer { buffer.removeFirst(buffer.count - Self.maxBuffer) }
        // 느슨(백그라운드 ~10초 깨어남)은 배치를 기다리지 않고 즉시 업로드
        if !wasPrecise || buffer.count >= Self.flushCount { flush() }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let previous = lastAuthStatus
        lastAuthStatus = authStatus
        // WhenInUse 로 막 바뀐 경우에만 Always 승격 요청 (반복 호출 방지)
        if isSharing, authStatus == .authorizedWhenInUse, previous != .authorizedWhenInUse {
            manager.requestAlwaysAuthorization()
        }
        if isSharing, isAuthorized {
            startLoose()
        } else {
            // 권한 회수 시 전부 중단
            stopPrecise()
            stopLoose()
            buffer.removeAll()
        }
        if authStatus != .authorizedAlways { manager.allowsBackgroundLocationUpdates = false }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // 일시적 실패는 무시 (다음 업데이트를 기다림)
    }

    // MARK: - 업로드

    private func startFlushTimer() {
        guard flushTimer == nil else { return }
        flushTimer = Timer.scheduledTimer(withTimeInterval: Self.flushInterval, repeats: true) { [weak self] _ in
            self?.flush(force: true)
        }
    }

    /// force: 타이머/정밀 종료 시에는 백오프 중이어도 시도
    private func flush(force: Bool = false) {
        guard isSharing, isAuthorized, TokenStore.shared.access != nil,
              !uploading, !buffer.isEmpty else { return }
        if !force, let failedAt = lastUploadFailureAt,
           Date().timeIntervalSince(failedAt) < Self.retryBackoff { return }
        let batch = buffer
        uploading = true
        LocationAPI.shared.uploadPoints(batch) { [weak self] result in
            guard let self = self else { return }
            self.uploading = false
            // 실패하면 버퍼에 남겨 다음 flush 에서 재시도. 공유가 꺼졌으면 이미 비워짐
            if case .success = result {
                self.lastUploadFailureAt = nil
                if self.isSharing { self.buffer.removeFirst(min(batch.count, self.buffer.count)) }
            } else {
                self.lastUploadFailureAt = Date()
            }
        }
    }
}
