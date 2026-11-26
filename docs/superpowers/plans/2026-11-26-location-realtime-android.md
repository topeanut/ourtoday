# 실시간 위치 공유 — Android 네이티브 (Part A-4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use `- [ ]`.

**Goal:** Android 앱이 (1) 웹에서 JWT를 받아 EncryptedSharedPreferences에 보관, (2) 공유 ON이면 FusedLocationProvider 저빈도로 느슨하게 위치를 모아 업로드(FGS 없이), (3) 상대가 "정확한 위치 보기"를 누르면 온 고우선순위 무음 FCM으로 Foreground Service를 띄워 3분간 정밀 업로드, (4) 웹과 브리지로 상태를 주고받는다. (웹 브리지는 A-3에서 완료 — Android도 OurTodayNative로 동작.)

**Architecture:** 기존 webview 래퍼(`mobile/android/app/`). 새 Kotlin: `TokenStore`(EncryptedSharedPreferences), `LocationApi`(HttpURLConnection 업로드/refresh), `LocationEngine`(FusedLocationProvider 느슨), `LocationForegroundService`(정밀 FGS), `BootReceiver`(재무장). `NativeBridge`에 authToken/locationShare/locationStatus 케이스 + `MainActivity`에 Handler 구현·웹 회신, `PushService`에 data-only 정밀 on/off.

**Spec:** `docs/superpowers/specs/2026-11-22-location-sharing-design.md` (Part A Android + 공통 JWT).

## Global Constraints

- **배포 의존**: 서버(A-1)·웹(A-2)은 로컬 main에만 → 런타임 동작엔 배포 필요. 범위 = **컴파일(assembleDebug) 통과 + 리뷰 + 설치 가능**. 백그라운드/FGS/무음FCM 실동작·종단은 배포·스토어심사 후(사용자 몫).
- API base/계약: iOS와 동일. base = `https://ourtoday.vercel.app/` + `api/v1`(웹이 백엔드로 프록시). `POST /location/points` body `{points:[{lat,lng,accuracy?,capturedAt(ISO),source("COARSE"|"PRECISE")}]}` Bearer. refresh `POST /auth/refresh` body `{"refreshToken"}` → `{accessToken, refreshToken}`(둘 다 저장). 401→refresh→1회 재시도→실패 시 중단(백오프).
- 무음 FCM: 서버가 `data: {type: "location_precise_on"|"location_precise_off", expiresAt?}` 고우선순위(priority high)로 보냄. **data-only 고우선순위는 백그라운드에서도 onMessageReceived 호출**(단, 배터리 최적화/Doze·강제종료 시 지연·미보장 — best-effort). geofence/passive가 보조 기동.
- 권한: FINE 먼저 → ACCESS_BACKGROUND_LOCATION 별도("항상 허용", 설정 이동 유도) → 13+ POST_NOTIFICATIONS(정밀 FGS 알림). 공유 OFF/권한거부/토큰없음이면 수집·업로드 전면 중지.
- 정밀 FGS: `foregroundServiceType=location`, 상주 알림 "위치 공유 중"(브랜드). 3분 타이머 또는 off 수신 시 `stopSelf`. (Android는 FGS 알림 숨김 불가 — iOS 파란 바 논쟁과 달리 알림 상주가 규칙.)
- 토큰은 우리 웹(앱 페이지)에서 온 브리지로만 수신·회신(기존 `NavigationPolicy.isAppPage` 가드 유지). 토큰 값 로깅 금지. 비밀값/키 커밋 금지.
- Kotlin 경고 최소, 기존 스타일(주석 한국어, mainHandler, coroutine 미사용 시 Executor/Thread). 새 의존: `play-services-location`, `androidx.security:security-crypto`.
- **google-services.json**은 gitignore라 worktree에 없음 → 빌드 전 main에서 복사(아래). 커밋 금지.
- 각 태스크 후 **빌드**: `cd mobile/android && ./gradlew assembleDebug 2>&1 | tail` → `BUILD SUCCESSFUL`.

## Review Focus

1. **프라이버시/안전**: 공유 OFF·권한거부·토큰없음이면 어떤 위치도 수집/업로드 안 됨. 토큰은 앱 페이지 브리지로만. FGS는 정밀 끝나면 반드시 `stopSelf`(상주 알림·수집 영구 금지).
2. **토큰 수명**: 401→refresh→1회 재시도, 실패 시 중단+백오프(요청 폭주 없음). EncryptedSharedPreferences 저장/삭제(로그아웃). 응답 키 accessToken/refreshToken 파싱·둘 다 저장.
3. **FGS 생명주기**: off FCM/3분/공유OFF에서 service stop + 타이머 정리. 중복 시작 방지. 13+ 알림 권한 없으면 FGS 시작 규칙 준수(startForeground 타입·예외처리).
4. **재무장**: BOOT_COMPLETED·프로세스 재생성 시 공유 ON이면 느슨 복구(토큰 있을 때).
5. **기존 기능 무회귀**: 푸시 알림(PushService notification 경로), 브리지 기존 케이스, 공유/권한 플럼빙(Part B) 유지. data-only와 notification 경로 분리.

---

### Task 1: 권한·FGS·부트 리시버·의존성 (Manifest + gradle)

**Files:** `mobile/android/app/src/main/AndroidManifest.xml`, `mobile/android/app/build.gradle.kts`.

- [ ] **Step 1: 권한** — Manifest에 추가: `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`(정밀 3분 타이머에 필요 시 — 가급적 Handler/coroutine로 회피). 기존 FINE/COARSE/POST_NOTIFICATIONS 유지.
- [ ] **Step 2: 서비스/리시버 선언** — `LocationForegroundService`(`foregroundServiceType="location"`, exported=false), `BootReceiver`(`RECEIVE_BOOT_COMPLETED` intent-filter). (클래스는 T2에서 작성 — 선언만 먼저 두면 빌드 깨지니 T2와 함께 맞추거나 T2 후 추가. 순서상 이 Step은 T2 클래스 생성 뒤 활성화.)
- [ ] **Step 3: 의존성** — `com.google.android.gms:play-services-location:21.3.0`(FusedLocation/Geofence), `androidx.security:security-crypto:1.1.0-alpha06`(EncryptedSharedPreferences). 버전은 기존 BOM/AGP와 호환되게.
- [ ] **Step 4: 빌드** — google-services.json 복사 후 `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: 커밋** — `git commit -m "feat(android): 위치 권한·FGS·부트·의존성"`

> 순서 메모: 서비스/리시버 Manifest 선언은 대상 클래스가 있어야 빌드되므로, 실제로는 T2에서 클래스 생성 후 Manifest 선언을 넣고 함께 빌드. T1에서는 권한+의존성+(클래스 없는) 빌드까지, 서비스 선언은 T2 커밋에 포함해도 됨.

---

### Task 2: 네이티브 위치 엔진 (TokenStore + LocationApi + LocationEngine + FGS + BootReceiver)

**Files (new):** `TokenStore.kt`, `LocationApi.kt`, `LocationEngine.kt`, `LocationForegroundService.kt`, `BootReceiver.kt` (모두 `app/ourtoday/`). + Manifest에 서비스/리시버 선언(T1 Step2).

**Interfaces (produce):**
- `TokenStore`(EncryptedSharedPreferences, app context): `save(access, refresh)`, `clear()`, `access:String?`, `refresh:String?`. MasterKey AES256.
- `LocationApi`(context): `uploadPoints(List<PointDTO>): Boolean`(동기, 백그라운드 스레드에서 호출; Bearer, 401→refresh→1회 재시도), `refresh(): Boolean`. HttpURLConnection + JSON. `PointDTO(lat:Double, lng:Double, accuracy:Double?, capturedAt:String ISO, source:String)`.
- `LocationEngine`(object/singleton, app context 주입): `var isSharing`(set 시 느슨 start/stop + SharedPreferences 영속), `startLoose()`(FusedLocationProvider 저빈도 — 예: interval 수분, balanced power; 또는 geofence/passive), `stopLoose()`, 수집 콜백→버퍼→배치 업로드(백그라운드 Executor). no-op 가드(공유/권한/토큰). `requestArm()`(부트/런치 복구: 영속 공유 ON && 토큰 있으면 startLoose).
- `LocationForegroundService`: onStartCommand에서 `startForeground(알림)` + FusedLocation 고빈도 수집→업로드, 3분 타이머/stop 액션에 `stopSelf`. 중복 시작 방지(이미 실행 중이면 타이머만 리셋). 알림 채널 "위치 공유 중".
- `BootReceiver`: BOOT_COMPLETED에 `LocationEngine.requestArm()`.

- [ ] **Step 1** — 위 파일 작성 + Manifest 서비스/리시버 선언. 느슨 수집은 FGS 없이(저빈도 콜백; Android가 백그라운드 제한하므로 best-effort). 정밀은 FGS에서만 고빈도. 버퍼 cap·flush 정책 iOS와 유사.
- [ ] **Step 2: 빌드** — `./gradlew assembleDebug` → BUILD SUCCESSFUL, 경고 최소.
- [ ] **Step 3: 커밋** — `git commit -m "feat(android): 위치 엔진·FGS·토큰 저장소·업로드"`

---

### Task 3: 브리지 + FCM data 정밀 깨우기 + 재무장 배선

**Files:** `NativeBridge.kt`(케이스+Handler 메서드), `MainActivity.kt`(Handler 구현 + 웹 회신 + 권한 요청 + 런치 재무장), `PushService.kt`(data-only 정밀 on/off).

- [ ] **Step 1: 브리지** — `NativeBridge.Handler`에 `onAuthToken(access:String?, refresh:String?, clear:Boolean)`, `onLocationShare(enabled:Boolean)`, `onLocationStatus()` 추가. `postMessage` when에 authToken/locationShare/locationStatus 케이스. MainActivity 구현: authToken(clear면 TokenStore.clear()+isSharing=false / 저장), locationShare(enabled면 권한 요청(FINE→백그라운드 유도)+isSharing=true, 아니면 false), locationStatus→웹 회신. 회신은 `ourtoday:locationStatus {sharing, authorization}`를 `webView.evaluateJavascript`(앱 페이지일 때만, 기존 sendPushState 패턴).
- [ ] **Step 2: FCM** — `PushService.onMessageReceived`에서 `message.data["type"]`가 `location_precise_on`이면 `LocationForegroundService` 시작(startForegroundService), `location_precise_off`면 stop 액션. 기존 notification 경로(title/body 있는 메시지)와 분리 — data type이 위치면 알림 표시 안 함.
- [ ] **Step 3: 재무장** — MainActivity onCreate(또는 Application)에서 `LocationEngine.requestArm()` 참조(프로세스 재생성 복구). BootReceiver는 T2에서 연결됨.
- [ ] **Step 4: 빌드** — `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: 커밋** — `git commit -m "feat(android): 위치 브리지 + FCM 정밀 깨우기 + 재무장"`

---

## 사용자가 직접 할 일 (출시 전)

- Google Play 백그라운드 위치 선언서 + 시연 영상(FGS location 사용 사유).
- 위치기반서비스사업자 신고(방통위), 개인정보처리방침·Play 데이터 보안 갱신.
- 서버(A-1)·웹(A-2) origin push + 배포(네이티브 종단 테스트 선결).
- 실기기: "항상 허용" 승격, 백그라운드 느슨 수집, 무음 FCM→FGS 정밀, 부트 재무장 확인.

## 빌더에게 맡기는 것

- 느슨 수집 방식(FusedLocation 저빈도 vs geofence/passive)과 주기, 버퍼 cap/flush.
- HTTP 클라이언트(HttpURLConnection 권장, 새 의존 회피) + 백그라운드 실행(Executor/Thread/WorkManager).
- 3분 타이머 구현(Handler/coroutine), FGS 알림 문구·채널.
- play-services-location / security-crypto 정확한 호환 버전.
