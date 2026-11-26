# 실시간 위치 공유 — iOS 네이티브 (Part A-3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use `- [ ]`.

**Goal:** iOS 앱이 (1) 웹에서 JWT를 받아 보관하고, (2) 공유 ON이면 평소 significant-change로 느슨하게 위치를 모아 업로드, (3) 상대가 "정확한 위치 보기"를 누르면 온 무음 푸시로 깨어나 3분간 정밀 위치를 업로드, (4) 웹과 브리지로 상태를 주고받는다. + 키보드 20pt 틈 수정.

**Architecture:** 기존 webview 래퍼(`mobile/ios/OurToday/`, XcodeGen `project.yml`). 새 네이티브 모듈 `LocationEngine`(CLLocationManager 느슨/정밀) + `TokenStore`(Keychain) + `LocationAPI`(URLSession 업로드/refresh). `WebViewController` 브리지에 `authToken`/`locationShare`/`locationStatus` 케이스 추가, `AppDelegate`에 무음 푸시 핸들러. 웹 `lib/location.ts`·`auth.ts`가 브리지로 토큰/공유상태 전달.

**Spec:** `docs/superpowers/specs/2026-11-22-location-sharing-design.md` (Part A iOS + 공통 JWT).

## Global Constraints

- **배포 의존**: 서버(A-1)·웹(A-2)은 현재 로컬 main에만 있음. 네이티브 런타임 동작엔 서버 배포 필요 → 본 플랜은 **컴파일(xcodebuild) 통과 + 리뷰 + 기기 설치 가능**까지가 범위. 종단 동작/백그라운드/무음푸시 실동작은 배포·스토어심사 후(사용자 몫).
- API base: 웹과 동일 출처 경유 — `AppConfig.startURL`(`https://ourtoday.vercel.app/`) + `api/v1`. (웹 Next.js rewrite가 백엔드로 프록시.) refresh는 `POST /api/v1/auth/refresh`(상태없는 재발급, 웹/네이티브 병행 안전).
- 엔드포인트: `POST /location/points` body `{points:[{lat,lng,accuracy?,capturedAt(ISO),source("COARSE"|"PRECISE")}]}` (Bearer). 업로드 실패/401 → refresh 후 1회 재시도, 그래도 실패면 다음 배치로 미룸(로컬 큐).
- 권한: WhenInUse 먼저 → Always 업그레이드 유도(iOS 13+ 첫 프롬프트 Always 직접 불가). 공유 OFF/권한거부면 수집·업로드 전면 중지.
- 정밀: `showsBackgroundLocationIndicator=false`(파란 바 숨김; **심사 반려 시 true로 폴백** — 상수 하나로 토글 가능하게). 3분 또는 off 푸시에 정밀 중지→느슨 복귀.
- best-effort 한계: 무음 푸시 OS throttle·강제종료 시 미보장. significant-change가 보조 기동.
- 브리지는 **우리 출처(main frame, startURL host)** 에서 온 메시지만 처리(기존 가드 유지). 토큰도 우리 페이지에만 전달.
- Swift 경고 0 목표, 기존 코드 스타일(주석 한국어, weak self, 메인스레드 콜백) 따름. 비밀값/키 커밋 금지.
- 각 태스크 후 **빌드**: `cd mobile/ios && xcodegen generate && xcodebuild -scheme OurToday -destination 'generic/platform=iOS' -configuration Debug build CODE_SIGNING_ALLOWED=NO 2>&1 | tail` → `BUILD SUCCEEDED`. (서명 불필요한 컴파일 확인.)

## Review Focus

1. **프라이버시/안전**: 공유 OFF·권한거부·토큰없음이면 어떤 위치도 수집/업로드되지 않음. 토큰은 우리 출처에만. 정밀은 3분 뒤 반드시 중지(타이머 누수/영구 정밀 금지).
2. **토큰 수명**: 401 시 refresh→재시도, refresh 실패 시 조용히 중단(무한 루프·연속 실패 폭주 없음). Keychain 저장/삭제(로그아웃 시) 정확.
3. **정밀 중지 보장**: off 푸시/3분/앱 상태전환에서 `stopUpdatingLocation`+타이머 정리. 중복 정밀 시작 방지.
4. **파란 바 폴백**: `showsBackgroundLocationIndicator` 토글이 상수 하나로 가능(심사 반려 대비).
5. **기존 기능 무회귀**: Apple 로그인·푸시·키보드(스크롤 수정)·에러뷰 동작 유지. 키보드 20pt 틈만 개선.

---

### Task 1: 권한·백그라운드 모드·entitlements 설정

**Files:** `mobile/ios/project.yml`(Info.plist 키·UIBackgroundModes·entitlements 매핑), 필요 시 `mobile/ios/OurToday/OurToday.entitlements`.

- [ ] **Step 1** — project.yml에 추가: `NSLocationAlwaysAndWhenInUseUsageDescription`(한국어 설명: 상대와 위치 공유), `NSLocationWhenInUseUsageDescription`은 기존 유지(문구 공유 목적 반영 검토), `UIBackgroundModes` = `[location, remote-notification]`. entitlements `aps-environment`는 이미 있음(유지).
- [ ] **Step 2** — `xcodegen generate` 후 `plutil -p` 로 Info.plist에 키 반영 확인.
- [ ] **Step 3: 빌드** — 위 xcodebuild → BUILD SUCCEEDED.
- [ ] **Step 4: 커밋** — `git commit -m "feat(ios): 위치 권한·백그라운드 모드·entitlements"`

---

### Task 2: 네이티브 위치 엔진 (TokenStore + LocationAPI + LocationEngine)

**Files (new):** `mobile/ios/OurToday/TokenStore.swift`, `LocationAPI.swift`, `LocationEngine.swift`. project.yml이 폴더 전체를 소스로 잡으면 자동 포함(확인).

**Interfaces (produce):**
- `TokenStore`(Keychain): `save(access:String, refresh:String)`, `clear()`, `var access/refresh:String?`. kSecClassGenericPassword, 서비스명 고정.
- `LocationAPI`: `uploadPoints([PointDTO], completion:(Result<Void,Error>))` (Bearer, 401→`refresh()`→1회 재시도), `refresh(completion:)` (`POST auth/refresh` with refresh token → 새 access 저장). 배경 적합하게 URLSession(default or background config). baseURL = startURL+api/v1.
- `LocationEngine: NSObject, CLLocationManagerDelegate`(singleton): `var isSharing:Bool`(set 시 start/stopLoose), `requestAuthorization()`(WhenInUse→Always 업그레이드), `startPrecise(seconds:180)`/`stopPrecise()`, `authStatus` 노출, 수집 포인트를 로컬 버퍼→주기/배치 `LocationAPI.uploadPoints`. 느슨=`startMonitoringSignificantLocationChanges`, 정밀=`startUpdatingLocation`+`showsBackgroundLocationIndicator = Self.showBlueBar`(상수 기본 false)+3분 타이머→stopPrecise. 콜백 메인스레드. 공유 OFF/권한거부/토큰없음이면 전부 no-op.

- [ ] **Step 1** — 세 파일 작성. `PointDTO{lat,lng,accuracy?,capturedAt,source}`. 포인트 버퍼는 과도하지 않게(예: 20개 또는 30초마다 flush). 중복 정밀 시작 가드, 타이머 정리.
- [ ] **Step 2: 빌드** — xcodebuild → SUCCEEDED (아직 아무도 호출 안 해도 컴파일).
- [ ] **Step 3: 커밋** — `git commit -m "feat(ios): 위치 엔진·토큰 저장소·업로드 API"`

---

### Task 3: 브리지 + 무음 푸시 배선

**Files:** `WebViewController.swift`(handle 케이스 추가 + ourtoday:location/locationStatus 송신), `AppDelegate.swift`(무음 푸시 핸들러), 필요 시 `PushManager.swift`.

**Interfaces (consume T2):** `LocationEngine.shared`, `TokenStore`.

- [ ] **Step 1: 브리지** — `handle(message:)`에 케이스:
  - `authToken` {access, refresh}: `TokenStore.save`. (로그아웃 신호 {clear:true} → `TokenStore.clear()` + `LocationEngine.isSharing=false`.)
  - `locationShare` {enabled:Bool}: enabled면 `requestAuthorization()`+`isSharing=true`, 아니면 `isSharing=false`. 결과 권한 상태를 `ourtoday:locationStatus`로 회신.
  - `locationStatus`: 현재 권한/공유 상태를 `ourtoday:locationStatus`로 회신.
  위치 업데이트를 웹에 알릴 필요가 있으면 `ourtoday:location` 이벤트(선택). 토큰/상태 송신은 기존 sendPushState처럼 **우리 출처일 때만**.
- [ ] **Step 2: 무음 푸시** — `AppDelegate`에 `application(_:didReceiveRemoteNotification:fetchCompletionHandler:)`: userInfo의 `type`이 `location_precise_on`이면 `LocationEngine.shared.startPrecise()`, `location_precise_off`면 `stopPrecise()`, 끝나면 completionHandler(.newData/.noData). (Firebase data 메시지 경로 확인.)
- [ ] **Step 3: 빌드** — xcodebuild → SUCCEEDED.
- [ ] **Step 4: 커밋** — `git commit -m "feat(ios): 위치 브리지 + 무음 푸시 정밀 깨우기"`

---

### Task 4: 웹 브리지 배선 (토큰 전달 + 공유 상태)

**Files:** `web/src/lib/auth.ts`(로그인/갱신 시 토큰을 네이티브로), `web/src/lib/location.ts`(공유 토글 시 `locationShare` 전송, `locationStatus` 조회·수신), 필요 시 마이페이지(권한 꺼짐 안내).

**Interfaces:** 네이티브 브리지 `window.webkit.messageHandlers.ourtoday.postMessage({type,...})`(iOS) — 기존 브리지 유틸이 있으면 재사용(앱 여부 `isApp()`). 이벤트 수신 `window.addEventListener('ourtoday:locationStatus', ...)`.

- [ ] **Step 1** — auth.ts: 로그인 성공·토큰 갱신·로그아웃 시 앱이면 `postBridge({type:'authToken', access, refresh})` / `{type:'authToken', clear:true}`. location.ts: `setSharing(true/false)` 시 앱이면 `postBridge({type:'locationShare', enabled})`; `locationStatus` 요청·`ourtoday:locationStatus` 수신 헬퍼. 이전 버전 앱(브리지 없음)은 조용히 무시(try/catch).
- [ ] **Step 2: 린트/빌드** — `cd web && npm run lint` 0 + `BACKEND_ORIGIN=... npm run build` 성공.
- [ ] **Step 3: 커밋** — `git commit -m "feat(web): 네이티브 토큰·공유상태 브리지"`

---

### Task 5: 키보드 20pt 틈 수정

**Files:** `WebViewController.swift`(`keyboardWillChange`).

- [ ] **Step 1** — 포커스된 입력칸 위 버튼이 키보드보다 ~20pt 떠 보이는 틈 제거. 현재 overlap=`view.bounds.maxY - frameInView.minY`. 원인 후보: 홈 인디케이터 safe-area가 overlap에 이중 반영되거나 웹 쪽 하단 패딩. 네이티브에서 조정 가능한 부분(예: safe-area 보정)만 손대고, 웹 패딩 문제면 그 지점 명시. 스크롤 수정(기존, `window.__ourtodayKeyboard`)은 건드리지 않음.
- [ ] **Step 2: 빌드** — xcodebuild → SUCCEEDED.
- [ ] **Step 3: 기기 확인 필요 표시** — 실제 틈 제거는 실기기 검증(사용자/후속). 코드 근거를 리포트에.
- [ ] **Step 4: 커밋** — `git commit -m "fix(ios): 키보드 위 버튼 20pt 틈 보정"`

---

## 사용자가 직접 할 일 (출시 전)

- iOS "항상 허용" + 파란 바 숨김 App Review 소명. 반려 시 `LocationEngine.showBlueBar=true`로 재빌드.
- 위치기반서비스사업자 신고(방통위), 개인정보처리방침·App Store 라벨 갱신.
- 서버(A-1)·웹(A-2) origin push + 배포(네이티브 종단 테스트 선결).

## 빌더에게 맡기는 것

- 포인트 버퍼 flush 주기·배치 크기, URLSession 구성(기본 vs 백그라운드), 로컬 큐 영속 여부(메모리면 충분).
- Firebase data 메시지 수신 경로(AppDelegate vs Messaging delegate) 중 동작하는 쪽.
- 키보드 틈 보정 정확한 수치(실기기 검증 전 best-effort).
