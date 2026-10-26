# 오늘의 우리 — 모바일 앱 (WebView 셸)

웹(`https://ourtoday.vercel.app/`)을 그대로 띄우는 Android / iOS 앱입니다. 화면·기능은 모두 웹에 있고, 앱은 WebView + 몇 가지 네이티브 기능(공유, 외부 링크, 사진 선택, 햅틱)만 담당합니다.

```
mobile/
├── android/   Kotlin, 단일 Activity, minSdk 26 / targetSdk 35, 패키지 app.ourtoday
└── ios/       Swift + UIKit(WKWebView), iOS 15+, 번들 ID app.ourtoday (xcodegen)
```

## 빌드 / 실행

### Android
- Android Studio 에서 `mobile/android` 폴더 열기 → Run
- 터미널: `cd mobile/android && ./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- `local.properties`(sdk.dir)는 git 에 안 올라감. 없으면 Android Studio 가 만들어 주거나 `sdk.dir=/Users/<나>/Library/Android/sdk` 로 직접 작성
- 디버그 빌드에서 크롬 `chrome://inspect` 로 WebView 디버깅 가능

### iOS
- Xcode 에서 `mobile/ios/OurToday.xcodeproj` 열기 → Signing & Capabilities 에서 Team 선택 → Run
- 터미널(시뮬레이터): `cd mobile/ios && xcodebuild -project OurToday.xcodeproj -scheme OurToday -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build`
- 프로젝트 파일은 `project.yml` 로 생성 (`brew install xcodegen` 후 `xcodegen generate`). 설정을 바꿀 땐 project.yml 을 고치고 다시 생성
- 디버그 빌드는 Safari > 개발자 메뉴에서 WebView 검사 가능 (iOS 16.4+)

## 시작 URL 바꾸기

| 플랫폼 | 위치 |
|--------|------|
| Android | `app/build.gradle.kts` 의 `START_URL`. 디버그만 임시로: `./gradlew installDebug -PstartUrl=http://10.0.2.2:3000/` (에뮬레이터 → 내 Mac 의 localhost) |
| iOS | `project.yml` 의 `OURTODAY_START_URL` → `xcodegen generate` (Info.plist `OurTodayStartURL` 로 들어감) |

앱 안에서 여는 도메인은 시작 URL 의 호스트 + 카카오 로그인 도메인뿐이라, 도메인을 바꾸면 이 값만 고치면 됩니다.

## 웹 ↔ 앱 약속

### 앱 감지
User-Agent 끝에 `OurTodayApp/1.0 (android)` / `OurTodayApp/1.0 (ios)` 가 붙습니다. 웹은 `/OurTodayApp/` 로 판단.

### 브리지 (웹 → 앱)
메시지는 `{ type, ... }` 형태의 객체입니다.

```js
// Android
window.OurTodayNative.postMessage(JSON.stringify(msg))
// iOS
window.webkit.messageHandlers.ourtoday.postMessage(msg)
```

| type | 필드 | 동작 |
|------|------|------|
| `share` | `url` (필수), `title?`, `text?` | 시스템 공유 시트 |
| `openExternal` | `url` | 시스템 브라우저로 열기 (예: 데이터 내보내기 zip) |
| `haptic` | — | 가벼운 진동 (지원 안 하면 무시) |

### 이동 정책 (메인 프레임만)
- **앱 안에서 열림**: 시작 URL 호스트 + 카카오 로그인 화면만 (`kauth.kakao.com`, `accounts.kakao.com`, `logins.kakao.com`, `accounts.daum.net`, `logins.daum.net`). 카카오 로그인이 다른 호스트로 넘어가 외부 브라우저가 열리면 이 목록에 추가
- **브리지 보안**: 우리 웹(시작 URL 호스트)의 메인 프레임에서 온 메시지만 처리, `openExternal` 은 http/https 만
- Android `allowBackup=false` (리프레시 토큰이 든 WebView 저장소가 다른 기기로 복원되지 않게)
- **시스템 브라우저**: 그 외 http(s) (예: Oracle 저장소 다운로드 링크)
- **OS 로 넘김**: `intent://`, `kakaotalk://`, `kakaokompassauth://`, `tel:`, `mailto:` 등
  - Android `intent://` 는 해당 앱이 없으면 `browser_fallback_url` → 없으면 플레이스토어

### 기타
- 로그인 토큰(JWT)은 웹의 localStorage 에 저장 → 앱을 껐다 켜도 유지 (Android DOM Storage, iOS 기본 영구 데이터 저장소)
- 사진 첨부 `<input type="file" accept="image/*" multiple>`: Android 는 시스템 사진 선택기(여러 장), iOS 는 WKWebView 기본 동작 (사진 보관함/카메라 권한 문구 Info.plist 에 있음)
- 당겨서 새로고침: 페이지 맨 위에서만
- 메인 페이지 로딩 실패 시 "연결할 수 없어요 / 다시 시도" 화면
- 안전 영역: iOS 는 WebView 가 화면 전체를 덮고 웹이 `env(safe-area-inset-*)` 로 처리 (`viewport-fit=cover` 필요). Android 는 시스템 바 영역만큼 WebView 에 여백을 줌 (웹의 inset 값은 0)
- Android 뒤로가기: 웹 기록이 있으면 뒤로, 없으면 앱 종료

## 카카오 로그인
WebView 안에서 카카오 **웹 로그인**(accounts.kakao.com)이 그대로 동작합니다. 로그인 페이지의 "카카오톡으로 로그인"은 `intent://`(Android) / `kakaokompassauth://`(iOS)로 카카오톡 앱에 넘깁니다. 다만 카카오톡 앱 인증 후 WebView 로 돌아오는 흐름은 카카오 정책상 보장되지 않아, 안 되면 ID/비밀번호 로그인을 쓰면 됩니다. 완전한 앱 로그인이 필요해지면 카카오 네이티브 SDK + 백엔드 토큰 교환 API 를 추가해야 합니다.

## 아이콘
- Android: 적응형 벡터 아이콘 `app/src/main/res/drawable/ic_launcher_foreground.xml` (하트 + 해)
- iOS: `scripts/make-icon.swift` 로 같은 디자인의 1024px PNG 생성 (App Store 규정상 알파 채널 제거 — 스크립트 첫 줄 참고)

## 출시 메모

### Android (Play 스토어)
1. 키스토어 생성 (git 에 올리지 말 것, `.gitignore` 에 `*.jks` 포함):
   `keytool -genkeypair -v -keystore ourtoday-release.jks -alias ourtoday -keyalg RSA -keysize 2048 -validity 10000`
2. Android Studio > Build > Generate Signed App Bundle 로 `.aab` 생성, 또는 `app/build.gradle.kts` 에 `signingConfigs` 를 추가하고 비밀번호는 `~/.gradle/gradle.properties` 나 환경변수로 주입
3. 릴리스 빌드는 R8 축소 사용 — 브리지 메서드는 `proguard-rules.pro` 로 보존됨
4. `versionCode` / `versionName` 올리기

### iOS (App Store)
1. Apple Developer 계정에서 번들 ID `app.ourtoday` 등록, Xcode Signing 에서 Team 지정
2. Product > Archive → Organizer 에서 업로드
3. 버전은 `project.yml` 의 `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION`
4. 심사 참고: 단순 웹 래핑 앱은 반려될 수 있음(가이드라인 4.2). 공유 시트·햅틱·사진 선택 등 네이티브 기능과 앱다운 UX(오프라인 화면, 안전 영역 대응)를 갖춘 상태로 제출 권장
