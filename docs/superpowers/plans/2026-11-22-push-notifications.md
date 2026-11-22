# 푸시 알림 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android·iOS 앱에서 상대 기록·작년 오늘·반응·커플 연결 4가지 푸시 알림을 받고, 누르면 해당 화면으로 이동한다.

**Architecture:** 네이티브 앱이 FCM 토큰을 받아 기존 웹뷰 브리지로 웹에 넘기고, 웹이 서버(`/api/v1/push-tokens`)에 등록한다. 서버는 도메인 이벤트(기록·반응·연결)를 커밋 후 비동기로 받아 Firebase Admin SDK 로 발송하고, 작년 오늘은 매일 20:00 KST 스케줄러가 보낸다. 알림 data 의 `path` 를 네이티브가 웹뷰에서 연다.

**Tech Stack:** Spring Boot 3 (Java 21, JPA, `com.google.firebase:firebase-admin`), Next.js 15 + Tailwind, Android Kotlin (Firebase BoM, firebase-messaging), iOS Swift (Firebase SPM `FirebaseMessaging`, XcodeGen `project.yml`).

**Spec:** `docs/superpowers/specs/2026-11-22-push-notifications-design.md`

## Global Constraints

- 알림 제목은 항상 `오늘의 우리`. 본문에 일기 내용(content)을 절대 넣지 않는다.
- 자기 자신에게는 보내지 않는다. 커플이 CONNECTED 가 아니면 보내지 않는다.
- 발송 실패·FCM 미설정이 기록·반응·연결 API 응답을 실패시키면 안 된다.
- 서비스 계정 키는 서버 `.env` 의 `FIREBASE_SERVICE_ACCOUNT`(base64 JSON). 비어 있으면 발송기는 아무것도 안 하는 구현으로 기동.
- `user_settings.notify_reaction` 은 `columnDefinition = "boolean not null default true"` (운영 `ddl-auto: update`).
- 네이티브가 여는 경로는 `/` 로 시작하고 `//` 로 시작하지 않는 우리 앱 경로만.
- 버전: Android versionCode 2 / versionName `1.1.0`, iOS MARKETING_VERSION `1.0.2` / CURRENT_PROJECT_VERSION 3.
- 시간대는 `Asia/Seoul`. 작년 오늘 발송은 cron `0 0 20 * * *`.
- 이전 앱 버전(브리지에 push 없음)에서 웹은 조용히 아무것도 안 한다.

## Review Focus

- 같은 폰에서 계정을 바꿔 로그인 → 이전 계정에 알림이 가면 안 됨 (토큰이 새 사용자로 옮겨져야 함). Task 1 테스트로 고정.
- 로그아웃 후 그 폰으로 알림이 오면 안 됨 (웹이 DELETE 호출, 실패해도 로그아웃은 진행). Task 6 테스트로 고정.
- 반응을 빠르게 여러 개 눌렀다 취소 → 알림 1번 (10분 중복 방지, 취소는 알림 없음). Task 4 테스트로 고정.
- 커플 해제 후 이전 상대의 행동으로 알림이 오면 안 됨 (CONNECTED 확인). Task 4 테스트로 고정.
- 만료·삭제된 토큰(앱 삭제)으로 보낸 실패 → 그 토큰 삭제, 나머지 기기에는 계속 발송. Task 2 테스트로 고정.

---

### Task 1: 서버 — 푸시 토큰 저장 API

**Files:**
- Create: `api/src/main/java/com/ourtoday/api/push/PushToken.java` (Entity, 테이블 `push_tokens`: id UUID, user_id UUID not null, token varchar(512) unique not null, platform varchar(10) not null, created_at, updated_at)
- Create: `api/src/main/java/com/ourtoday/api/push/PushPlatform.java` (enum `ANDROID`, `IOS`; JSON 값은 소문자 `android`/`ios` — `@JsonCreator`/`@JsonValue`)
- Create: `api/src/main/java/com/ourtoday/api/push/PushTokenRepository.java`
- Create: `api/src/main/java/com/ourtoday/api/push/PushTokenService.java`
- Create: `api/src/main/java/com/ourtoday/api/push/PushTokenController.java`
- Create: `api/src/main/java/com/ourtoday/api/push/dto/PushTokenRegisterRequest.java`, `dto/PushTokenDeleteRequest.java`
- Modify: `api/src/main/java/com/ourtoday/api/account/AccountService.java` (withdraw 에서 `pushTokenService.deleteAllOf(userId)`)
- Test: `api/src/test/java/com/ourtoday/api/push/PushTokenApiTest.java`

**Interfaces:**
- Produces: `PushTokenRepository.findByUserIdIn(Collection<UUID> userIds): List<PushToken>`, `PushTokenRepository.deleteByToken(String token)`, `PushTokenService.register(UUID userId, String token, PushPlatform platform)`, `PushTokenService.delete(UUID userId, String token)`, `PushTokenService.deleteAllOf(UUID userId)`, `PushToken.getToken()`, `PushToken.getUserId()`.
- API: `POST /api/v1/push-tokens` body `{ "token": string(1..512), "platform": "android"|"ios" }` → 204. `DELETE /api/v1/push-tokens` body `{ "token": string }` → 204 (내 토큰이 아니면 아무것도 안 함). 둘 다 인증 필요(로그인만, 커플 불필요).

- [ ] **Step 1: 실패 테스트 작성** — `PushTokenApiTest` (SpringBootTest + MockMvc, 기존 `SessionCookieAuthTest` 처럼 `userService.findOrCreateByKakaoId` + `tokenProvider.generateAccessToken`):
  - `토큰을_등록하면_저장된다`: POST → 204, repository 에 (user, token, ANDROID) 1건.
  - `같은_토큰을_다시_등록해도_한_건이다`: 두 번 POST → 1건, platform 이 마지막 값.
  - `다른_계정이_같은_토큰을_등록하면_새_계정으로_옮겨진다`: A 등록 → B 등록 → token 의 userId == B, 총 1건.
  - `삭제하면_사라지고_남의_토큰은_지우지_않는다`: A 등록, B 가 DELETE → 그대로 1건; A 가 DELETE → 0건.
  - `잘못된_platform_은_400`: platform `"web"` → 400 `VALIDATION_FAILED`.
  - `탈퇴하면_토큰도_삭제된다`: 등록 후 `accountService.withdraw(A)` → 0건.
- [ ] **Step 2: 실행해서 실패 확인** — `cd api && ./gradlew test --tests '*PushTokenApiTest'` → 컴파일 실패/FAIL.
- [ ] **Step 3: 구현** — upsert 는 `findByToken` 후 있으면 userId·platform 갱신, 없으면 저장. API 컨벤션(record DTO + `@Valid`, `@Tag("Push")`) 따름.
- [ ] **Step 4: 테스트 통과 확인** — 같은 명령 PASS, 이어서 `./gradlew test` 전체 PASS.
- [ ] **Step 5: 커밋** — `feat(api): 푸시 토큰 등록·삭제 API`

### Task 2: 서버 — 발송기(PushSender)와 Firebase 설정

**Files:**
- Modify: `api/build.gradle.kts` (`implementation("com.google.firebase:firebase-admin:9.4.3")`)
- Create: `api/src/main/java/com/ourtoday/api/push/PushMessage.java` (record `PushMessage(String body, String path, String type)`)
- Create: `api/src/main/java/com/ourtoday/api/push/PushSender.java` (interface)
- Create: `api/src/main/java/com/ourtoday/api/push/FcmPushSender.java`
- Create: `api/src/main/java/com/ourtoday/api/push/NoopPushSender.java`
- Create: `api/src/main/java/com/ourtoday/api/push/PushConfig.java`
- Create: `api/src/main/java/com/ourtoday/api/push/PushDispatcher.java`
- Modify: `api/src/main/resources/application.yml` (`app.push.firebase-service-account: ${FIREBASE_SERVICE_ACCOUNT:}`, `app.push.async: true`), `api/src/test/resources/application.yml` (`app.push.firebase-service-account:` 빈 값, `app.push.async: false`), `.env.example` (`FIREBASE_SERVICE_ACCOUNT=` 줄)
- Test: `api/src/test/java/com/ourtoday/api/push/PushDispatcherTest.java`, `api/src/test/java/com/ourtoday/api/push/FakePushSender.java` (테스트용 `@Primary` 빈, 보낸 메시지를 리스트로 기록, 지정 토큰은 `SendResult.INVALID_TOKEN` 반환)

**Interfaces:**
- Produces:
  - `enum SendResult { OK, INVALID_TOKEN, FAILED }`
  - `PushSender.send(String token, PushMessage message): SendResult`
  - `PushDispatcher.sendToUsers(Collection<UUID> userIds, PushMessage message): void` — 대상 사용자의 모든 토큰에 발송, `INVALID_TOKEN` 이면 `deleteByToken`. 예외는 로그만 남기고 삼킨다.
  - `PushConfig`: `app.push.firebase-service-account` 가 비어 있으면 `NoopPushSender` 빈, 아니면 base64 디코드 → `FirebaseApp.initializeApp` → `FcmPushSender`. `app.push.async=true` 면 `@EnableAsync` 와 `pushExecutor`(core 1, max 2, queue 100) 등록.
- `FcmPushSender`: `Message.builder().setToken(token).setNotification(Notification.builder().setTitle("오늘의 우리").setBody(body).build()).putData("path", path).putData("type", type)`, Android `AndroidConfig` 채널 id `"records"`, APNs `ApsAlert` + sound `default`. `MessagingErrorCode.UNREGISTERED`/`INVALID_ARGUMENT` → `INVALID_TOKEN`, 그 외 예외 → `FAILED`.

- [ ] **Step 1: 실패 테스트 작성** — `PushDispatcherTest`:
  - `사용자의_모든_기기로_보낸다`: 사용자 A 토큰 2개 → FakePushSender 기록 2건, body·path 그대로.
  - `무효_토큰은_삭제하고_나머지는_보낸다`: 토큰 t1(무효 지정), t2 → t1 삭제됨, t2 발송 기록 있음.
  - `발송기가_예외를_던져도_밖으로_새지_않는다`: Fake 가 RuntimeException → `sendToUsers` 정상 반환.
  - `서비스계정이_없으면_Noop_발송기가_뜬다`: 별도 `@SpringBootTest(properties = "app.push.firebase-service-account=")` 컨텍스트에서 `PushSender` 빈 타입이 `NoopPushSender` (FakePushSender 없이).
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*PushDispatcherTest'`.
- [ ] **Step 3: 구현.**
- [ ] **Step 4: 통과 확인** — 해당 테스트 + 전체 `./gradlew test` PASS.
- [ ] **Step 5: 커밋** — `feat(api): FCM 발송기와 디스패처`

### Task 3: 서버 — 반응 알림 설정 추가

**Files:**
- Modify: `api/src/main/java/com/ourtoday/api/user/UserSettings.java` (`notifyReaction` 필드, columnDefinition 은 Global Constraints 값; `updateNotificationSettings(Boolean memoryToday, Boolean partnerPost, Boolean reaction)` 로 바꾸고 null 은 유지)
- Modify: `api/src/main/java/com/ourtoday/api/user/dto/UserSettingsResponse.java`, `dto/UserSettingsUpdateRequest.java` (필드 `Boolean notifyReaction`, 요청의 세 필드 모두 nullable), `UserService.java` 호출부
- Modify: `web/src/lib/types.ts` `Settings` 에 `notifyReaction: boolean`
- Test: 기존 설정 테스트 파일이 있으면 그 파일에, 없으면 `api/src/test/java/com/ourtoday/api/user/UserSettingsTest.java`

**Interfaces:**
- Produces: `UserSettings.isNotifyReaction()`, `UserSettingsRepository.findAllById(Iterable<UUID>)`(기존 JPA 메서드 사용).

- [ ] **Step 1: 실패 테스트** — `기본값은_반응_알림_켜짐`, `notifyReaction_만_보내면_나머지는_그대로` (PATCH `{ "notifyReaction": false }` → 응답 `notifyMemoryToday`·`notifyPartnerPost` 는 이전 값, `notifyReaction` false), `기존_클라이언트처럼_두_필드만_보내도_된다` (PATCH `{notifyMemoryToday:false, notifyPartnerPost:true}` → 200, `notifyReaction` 은 이전 값).
- [ ] **Step 2: 실패 확인.**
- [ ] **Step 3: 구현.**
- [ ] **Step 4: 통과 확인** — 전체 `./gradlew test` PASS.
- [ ] **Step 5: 커밋** — `feat(api): 반응 알림 설정`

### Task 4: 서버 — 상대 기록·반응·커플 연결 알림

**Files:**
- Create: `api/src/main/java/com/ourtoday/api/push/event/PostCreatedEvent.java` (record `PostCreatedEvent(UUID postId, UUID authorId, UUID coupleId, LocalDate postedDate)`)
- Create: `.../push/event/ReactionAddedEvent.java` (record `ReactionAddedEvent(UUID postId, UUID reactorId)`)
- Create: `.../push/event/CoupleConnectedEvent.java` (record `CoupleConnectedEvent(UUID coupleId, UUID joinerId)`)
- Create: `api/src/main/java/com/ourtoday/api/push/PushNotifier.java`
- Create: `api/src/main/java/com/ourtoday/api/push/Josa.java`
- Modify: `PostService.create` (저장 후 `publisher.publishEvent(new PostCreatedEvent(...))`), `ReactionService.toggle` (추가된 경우에만 `ReactionAddedEvent`), `CoupleService.join` (`connect` 후 `CoupleConnectedEvent`)
- Test: `api/src/test/java/com/ourtoday/api/push/PushNotifierTest.java`, `api/src/test/java/com/ourtoday/api/push/JosaTest.java`

**Interfaces:**
- Consumes: Task 2 `PushDispatcher.sendToUsers`, `PushMessage`; Task 3 `UserSettings.isNotifyReaction()`; `CoupleService.getPartnerId(UUID coupleId, UUID userId)`, `CoupleMemberRepository.findByCoupleIdOrderByJoinedAtAsc`.
- Produces: `Josa.iGa(String name): String` — 마지막 글자가 한글이면 받침 있으면 `"이"`, 없으면 `"가"`, 한글이 아니면 `"이(가)"`. `PushNotifier` 의 리스너 메서드는 `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async("pushExecutor")`.
- 문구·경로 (스펙 그대로):
  - 상대 기록: `"{닉네임}{iGa} 오늘의 기록을 남겼어요"`, 기록 날짜가 오늘(KST)이 아니면 `"{닉네임}{iGa} {M}월 {D}일의 기록을 남겼어요"`, path `/posts/{postId}`, type `partner_post`, 대상: 상대(설정 `notifyPartnerPost`).
  - 반응: `"{닉네임}{iGa} 내 기록에 반응했어요"`, path `/posts/{postId}`, type `reaction`, 대상: 기록 작성자(설정 `notifyReaction`, 작성자 == 반응자면 안 보냄), 같은 (reactorId, postId) 는 10분 안에 한 번 (`ConcurrentHashMap<String, Instant>` + 주입된 `Clock`).
  - 연결: `"{닉네임}님과 연결됐어요. 첫 기록을 남겨 보세요"`(닉네임 = 합류한 사람), path `/`, type `couple_connected`, 대상: 합류한 사람이 아닌 멤버 1명(설정 무관).

- [ ] **Step 1: 실패 테스트** — `JosaTest`: `민지`→`가`, `지호`→`가`, `정한`→`이`, `Alex`→`이(가)`. `PushNotifierTest`(FakePushSender, 커플 연결된 A(민지)·B(지호) 준비, `app.push.async=false` 라 동기 실행; 이벤트가 커밋 후 처리되도록 테스트 메서드는 `@Transactional` 없이 서비스 메서드를 직접 호출):
  - `상대가_오늘_기록하면_나에게_간다`: B 가 오늘 기록 → A 토큰으로 body `"지호가 오늘의 기록을 남겼어요"`, path `/posts/{id}`; B 토큰으로는 없음.
  - `지난_날짜_기록은_날짜를_넣는다`: postedDate 11월 20일 → `"지호가 11월 20일의 기록을 남겼어요"`.
  - `상대_기록_알림을_끄면_안_간다`.
  - `반응하면_작성자에게_가고_10분_안_중복은_한_번`: B 가 A 글에 HEART 추가→취소→LAUGH 추가 → 발송 1건, body `"지호가 내 기록에 반응했어요"`; Clock 11분 뒤로 → 다시 추가 시 2건째.
  - `내_글에_내가_반응하면_안_간다`, `반응_알림을_끄면_안_간다`.
  - `커플이_해제되면_안_간다`: 해제 후 이벤트 직접 발행(`publisher.publishEvent(new PostCreatedEvent(...))` 트랜잭션 안에서) → 0건.
  - `합류하면_초대한_사람에게만_간다`: A 가 초대 생성, C(닉네임 하늘) 합류 → A 에게 `"하늘님과 연결됐어요. 첫 기록을 남겨 보세요"`, path `/`.
  - `알림이_실패해도_기록_저장은_성공한다`: Fake 가 예외 → `PostService.create` 정상 반환, 글 존재.
- [ ] **Step 2: 실패 확인.**
- [ ] **Step 3: 구현.**
- [ ] **Step 4: 통과 확인** — 전체 `./gradlew test` PASS.
- [ ] **Step 5: 커밋** — `feat(api): 상대 기록·반응·커플 연결 알림`

### Task 5: 서버 — 작년 오늘 알림 (매일 20:00)

**Files:**
- Create: `api/src/main/java/com/ourtoday/api/push/MemoryTodayScheduler.java`
- Modify: `api/src/main/java/com/ourtoday/api/couple/CoupleRepository.java` (`List<Couple> findByStatus(CoupleStatus status)`)
- Modify: `PushConfig` (`@EnableScheduling`, 테스트에서는 `app.push.schedule: false` 로 끔 — 스케줄 등록만 끄고 메서드는 직접 호출 가능)
- Test: `api/src/test/java/com/ourtoday/api/push/MemoryTodaySchedulerTest.java`

**Interfaces:**
- Consumes: `PostRepository.findMemories(coupleId, month, day, before)`(기존; before = 올해 1월 1일 → 지난 연도만), Task 2 디스패처, `UserSettings.isNotifyMemoryToday()`.
- Produces: `MemoryTodayScheduler.sendMemoryToday(): int` (보낸 커플 수 반환), `@Scheduled(cron = "0 0 20 * * *", zone = "Asia/Seoul")` 로 호출.
- 문구: 가장 최근 기록 연도와 올해 차이 N 으로 `"{N}년 전 오늘, 둘이 남긴 기록이 있어요"`, path `/`, type `memory_today`. 홈 카드("1년 전 오늘")와 같은 기준(같은 월·일, 지난 연도 아무 해)이다 — 스펙의 "정확히 1년 전"보다 홈 카드와 맞추는 쪽을 택함(설계자 판단, 홈에서 보이는 것과 알림이 어긋나지 않게).

- [ ] **Step 1: 실패 테스트** (Clock 을 2026-11-22 20:00 KST 로 고정):
  - `작년_같은_날_기록이_있으면_둘_다에게_간다`: 2025-11-22 기록 → A·B 각 1건, body `"1년 전 오늘, 둘이 남긴 기록이 있어요"`.
  - `2년_전만_있으면_2년_전이라고_쓴다`: 2024-11-22 만 → `"2년 전 오늘, …"`.
  - `올해_기록이나_다른_날은_대상이_아니다`: 2026-11-22, 2025-11-21 기록만 → 0건.
  - `설정을_끈_사람은_빠진다`: A 끔 → B 만 1건.
  - `연결되지_않은_커플은_빠진다`.
- [ ] **Step 2: 실패 확인.**
- [ ] **Step 3: 구현.**
- [ ] **Step 4: 통과 확인** — 전체 PASS.
- [ ] **Step 5: 커밋** — `feat(api): 작년 오늘 알림 스케줄러`

### Task 6: 웹 — 브리지·토큰 등록·로그아웃 해제

**Files:**
- Create: `web/src/lib/push.ts`
- Modify: `web/src/lib/native.ts` (`NativeMessage` 에 `{ type: "pushStatus" }`, `{ type: "pushPermission" }`, `{ type: "openNotificationSettings" }` 추가)
- Modify: `web/src/lib/auth.ts` `logout()` (먼저 `unregisterPush()` 를 기다리되 1.5초 넘으면 그냥 진행)
- Modify: `web/src/components/Session.tsx`(SessionGate) — 로그인 상태로 마운트되면 `syncPushToken()` 1회
- Test: 웹 테스트 러너가 없으므로 `web/src/lib/push.ts` 는 로컬 브라우저에서 `window.dispatchEvent(new CustomEvent('ourtoday:push', …))` 로 확인 (Step 4).

**Interfaces:**
- 네이티브 → 웹 이벤트: `window.dispatchEvent(new CustomEvent("ourtoday:push", { detail: { permission: "granted"|"denied"|"default", token: string|null, platform: "android"|"ios" } }))`. 네이티브는 `pushStatus`·`pushPermission` 요청마다, 그리고 토큰이 바뀔 때 이 이벤트를 보낸다.
- Produces (`push.ts`):
  - `type PushState = { supported: boolean; permission: "granted"|"denied"|"default"; token: string|null; platform: "android"|"ios"|null }`
  - `getPushState(timeoutMs = 1500): Promise<PushState>` — 앱이 아니거나 응답이 없으면 `{supported:false, permission:"default", token:null, platform:null}`.
  - `requestPushPermission(): Promise<PushState>` — 결과가 granted 이고 token 있으면 서버 등록까지.
  - `syncPushToken(): Promise<void>` — granted+token 이면 `POST /push-tokens`, 마지막 등록 토큰을 localStorage `ourtoday.pushToken` 에 저장. 토큰 갱신 이벤트를 듣고 다시 등록.
  - `unregisterPush(): Promise<void>` — 저장된 토큰으로 `DELETE /push-tokens`, 실패 무시, 저장값 삭제.
  - `openNotificationSettings(): void`.

- [ ] **Step 1: 구현** (`api()` 헬퍼 사용, 모든 localStorage 접근은 try/catch).
- [ ] **Step 2: 타입·린트** — `cd web && npx tsc --noEmit -p . && npx eslint src/lib/push.ts src/lib/native.ts src/lib/auth.ts src/components/Session.tsx` → 오류 0.
- [ ] **Step 3: 로컬 확인 준비** — `ops/local.sh` 로 띄우고 minji 로 로그인.
- [ ] **Step 4: 브라우저 확인** — 콘솔에서 `window.OurTodayNative = { postMessage: (j) => { const m = JSON.parse(j); if (m.type === "pushStatus") setTimeout(() => dispatchEvent(new CustomEvent("ourtoday:push", { detail: { permission: "granted", token: "local-test-token", platform: "android" } }))) } }` 후 새로고침 없이 `syncPushToken` 이 호출되도록 페이지 이동 → 네트워크에 `POST /api/v1/push-tokens` 204. 로그아웃 → `DELETE /api/v1/push-tokens` 요청 후 `/login` 이동. 브리지 없는 상태에서는 아무 요청도 없음.
- [ ] **Step 5: 커밋** — `feat(web): 푸시 토큰 등록·해제 브리지`

### Task 7: 웹 — 권한 묻기 화면과 마이페이지 알림 카드

**Files:**
- Create: `web/src/components/PushPrompt.tsx` (`PushInviteNudge`, `PushWelcomeSheet`)
- Modify: `web/src/app/onboarding/couple/page.tsx` (초대 카드 아래 `PushInviteNudge`)
- Modify: `web/src/app/(main)/page.tsx` (홈 첫 진입 시 `PushWelcomeSheet`)
- Modify: `web/src/app/(main)/me/page.tsx` (알림 카드)

**Interfaces:**
- Consumes: Task 6 `getPushState`, `requestPushPermission`, `openNotificationSettings`; Task 3 `Settings.notifyReaction`.
- 문구 (그대로 사용):
  - `PushInviteNudge`: 앱 && permission `default` 일 때만, 초대 카드 아래 한 줄 `"상대가 들어오면 알려 드릴게요"` + 작은 버튼 `"알림 받기"`. 누르면 `requestPushPermission`, granted 면 `"알림을 켰어요"` 토스트 후 숨김.
  - `PushWelcomeSheet`: 앱 && permission `default` && localStorage `ourtoday.pushAsked` 없음일 때 홈 첫 진입에 Sheet. 제목 `"알림을 받아 볼까요?"`, 본문 `"상대가 기록을 남기거나 내 기록에 반응하면 바로 알려 드려요. 기록 내용은 알림에 보이지 않아요."`, 버튼 `"알림 받기"`(primary), `"나중에"`(ghost). 어느 쪽이든 `ourtoday.pushAsked=1` 저장.
  - 마이페이지 알림 카드: `"앱 푸시 알림은 준비 중이에요…"` 문구 삭제. 토글 3개 순서 `상대가 기록하면 알림`, `내 기록에 반응하면 알림`, `작년 오늘 알림`(설명 줄 `"매일 저녁 8시, 지난 오늘의 기록이 있을 때"`). 상태 안내: 웹 브라우저 → `"알림은 앱에서 받을 수 있어요"`; 앱 && denied → `"휴대폰 설정에서 알림이 꺼져 있어요"` + 버튼 `"설정 열기"`(openNotificationSettings); 앱 && default → `"알림 받기"` 버튼.

- [ ] **Step 1: 구현** (기존 `Sheet`, `Button`, `Toggle`, `useToast` 사용, 이모지 금지).
- [ ] **Step 2: 타입·린트** — tsc + eslint 오류 0.
- [ ] **Step 3: 로컬 렌더 확인** — Task 6 Step 4 의 가짜 브리지로 permission `default`/`denied`/`granted` 각각에서 마이페이지 카드, 홈 시트(첫 진입만), 초대 카드 아래 안내를 390×844 스크린샷으로 확인. 브리지 없음(웹)에서는 시트·안내가 안 뜨고 마이페이지에 `"알림은 앱에서 받을 수 있어요"`.
- [ ] **Step 4: impeccable detect** — `/Users/a11/.claude/skills/impeccable/scripts/impeccable detect --json <변경 파일>` → `[]`.
- [ ] **Step 5: 커밋** — `feat(web): 알림 권한 안내와 마이페이지 알림 설정`

### Task 8: Android — FCM 수신과 브리지

**Files:**
- Modify: `mobile/android/build.gradle.kts` (`id("com.google.gms.google-services") version "4.4.2" apply false`)
- Modify: `mobile/android/app/build.gradle.kts` (플러그인 적용, `implementation(platform("com.google.firebase:firebase-bom:33.7.0"))`, `implementation("com.google.firebase:firebase-messaging")`, versionCode 2 / versionName "1.1.0")
- Add: `mobile/android/app/google-services.json` (Firebase 콘솔에서 받음; 공개돼도 되는 설정이지만 저장소 정책상 커밋함)
- Create: `mobile/android/app/src/main/java/app/ourtoday/PushService.kt` (`FirebaseMessagingService`)
- Create: `mobile/android/app/src/main/java/app/ourtoday/PushBridge.kt` (권한·토큰 조회, 결과 이벤트 JS 생성)
- Create: `mobile/android/app/src/main/res/drawable/ic_stat_notify.xml` (단색 벡터, 앱 아이콘 하트 실루엣)
- Modify: `AndroidManifest.xml` (`POST_NOTIFICATIONS` 권한, `PushService` 서비스 + `com.google.firebase.MESSAGING_EVENT`, 기본 아이콘·채널 meta-data `default_notification_channel_id=records`)
- Modify: `NativeBridge.kt` (Handler 에 `onPushStatus()`, `onPushPermission()`, `onOpenNotificationSettings()`; type `pushStatus`/`pushPermission`/`openNotificationSettings`)
- Modify: `MainActivity.kt` (런처로 권한 요청, 결과를 `ourtoday:push` 이벤트로 웹에 전달, 알림 탭 intent extra `path` 처리 — onCreate/onNewIntent 모두, 경로 검증은 Global Constraints)
- Modify: `mobile/android/app/src/main/res/values/strings.xml` (`notification_channel_records` = "기록 알림")

**Interfaces:**
- Consumes: Task 6 이벤트 형식(`permission`, `token`, `platform:"android"`).
- `PushService.onMessageReceived`: 앱이 포그라운드일 때 notification 을 직접 표시(채널 `records`, 아이콘 `ic_stat_notify`, PendingIntent → MainActivity extra `path`). `onNewToken`: SharedPreferences 에 저장하고 MainActivity 가 살아 있으면 이벤트 전송.
- 백그라운드 알림 탭: FCM 이 data 를 launcher intent extras 로 넣으므로 `intent.getStringExtra("path")` 로 동일 처리.

- [ ] **Step 1: 구현.**
- [ ] **Step 2: 빌드** — `cd mobile/android && ./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 3: 실기기 설치** — 갤럭시 연결(`adb devices`), 디버그 빌드 `START_URL` 을 로컬 웹(`http://<Mac LAN IP>:3100/`)으로 바꿔 설치 (기존 iOS 디버그와 같은 방식).
- [ ] **Step 4: 실기기 확인** — 마이페이지 `알림 받기` → 시스템 권한 대화상자 → 허용 → 서버 로그/DB 에 토큰 1건. Firebase 콘솔 "테스트 메시지"로 그 토큰에 data `path=/bucket` 발송 → 앱 백그라운드·포그라운드 각각 알림 표시, 탭하면 버킷 화면.
- [ ] **Step 5: 커밋** — `feat(android): FCM 푸시 알림 (1.1.0)`

### Task 9: iOS — FCM 수신과 브리지

**Files:**
- Modify: `mobile/ios/project.yml` (SPM 패키지 `firebase-ios-sdk` from 11.6.0, 제품 `FirebaseMessaging`; entitlements `aps-environment: production`; `MARKETING_VERSION 1.0.2`, `CURRENT_PROJECT_VERSION 3`) 후 `xcodegen generate`
- Add: `mobile/ios/OurToday/GoogleService-Info.plist` (Firebase 콘솔에서 받음)
- Create: `mobile/ios/OurToday/PushManager.swift` (Firebase 구성, `UNUserNotificationCenterDelegate`, `MessagingDelegate`, 권한·토큰 상태, 탭한 알림의 path 전달)
- Modify: `AppDelegate.swift` (`FirebaseApp.configure()`, `PushManager.shared.start()`, `didRegisterForRemoteNotificationsWithDeviceToken` → `Messaging.messaging().apnsToken`)
- Modify: `WebViewController.swift` (`handle(message:)` 에 `pushStatus`/`pushPermission`/`openNotificationSettings`; 결과를 `ourtoday:push` 이벤트로; PushManager 의 탭 경로 콜백 → 우리 도메인 경로면 웹뷰 로드)

**Interfaces:**
- Consumes: Task 6 이벤트 형식(`platform:"ios"`).
- 포그라운드 수신 시 `[.banner, .sound]` 로 표시. 권한 요청은 `pushPermission` 을 받을 때만. 설정 열기는 `UIApplication.openNotificationSettingsURLString`.

- [ ] **Step 1: 구현.**
- [ ] **Step 2: 빌드** — `cd mobile/ios && xcodegen generate && xcodebuild -project OurToday.xcodeproj -scheme OurToday -destination 'generic/platform=iOS' -configuration Debug build` → BUILD SUCCEEDED.
- [ ] **Step 3: 실기기 확인** — 아이폰 12 mini 에 디버그 빌드(로컬 웹) 설치 → 마이페이지 `알림 받기` → 허용 → 토큰 등록 확인 → Firebase 콘솔 테스트 메시지(path `/bucket`) 수신·탭 이동. (APNs 키가 Firebase 에 올라가 있어야 함 — Task 10 의 사용자 단계 1·2 선행)
- [ ] **Step 4: 커밋** — `feat(ios): FCM 푸시 알림 (1.0.2)`

### Task 10: 운영 연결·문서·배포

**Files:**
- Modify: `landing/src/pages/privacy.astro` (수집 항목에 "푸시 알림 기기 토큰 — 알림 발송, 로그아웃·탈퇴 시 삭제", 처리 위탁에 "Google LLC (Firebase Cloud Messaging) — 푸시 알림 발송")
- Modify: `DEPLOY.md` (FIREBASE_SERVICE_ACCOUNT 설정 방법), `API.md`(로컬 문서, 푸시 토큰 API)

**사용자 단계 (Claude 는 화면·명령을 준비하고, 약관·로그인·비밀값 입력·제출은 사용자):**
1. Firebase 콘솔: Android 앱(`app.ourtoday`)·iOS 앱(`app.ourtoday`) 등록 → 설정 파일 2개 받기 (Claude 진행 가능).
2. Apple Developer → Keys → APNs 키 생성(.p8, `~/.secrets/ourtoday/` 보관) → Firebase 프로젝트 설정 > 클라우드 메시징 > APNs 인증 키 업로드 (키 ID, 팀 ID `NRJ8TYHKJM`).
3. Firebase 서비스 계정 키 생성 → 서버 `.env` 에 base64 로 `FIREBASE_SERVICE_ACCOUNT` 추가 → `docker compose up -d` 재기동 (명령은 Claude 가 `/copy` 형식으로 준비).

- [ ] **Step 1: 서버·웹 배포** — main push (api 자동 배포), 웹은 `web/README.md` 방식으로 Vercel 배포. 운영 `POST /api/v1/push-tokens`(토큰 없이) → 401 이면 배포 확인.
- [ ] **Step 2: 운영 발송 확인** — 사용자 단계 3 후 서버 로그에 `FcmPushSender` 초기화 로그, 운영 앱(릴리스 빌드)으로 상대 기록 알림 실제 수신.
- [ ] **Step 3: 개인정보처리방침 배포** — landing 빌드·배포, 페이지에서 문구 확인.
- [ ] **Step 4: 스토어** — Android 릴리스 AAB(1.1.0, versionCode 2) 빌드 → 비공개 테스트 트랙 업로드(사용자 확인 후), Play 프로덕션 액세스 승인 후 프로덕션 출시에 같은 빌드. iOS 1.0.2 archive → 업로드 → 1.0.1 통과 후 심사 제출(사용자 확인). 두 스토어 데이터 보안·개인정보 라벨에 기기 토큰 항목 추가(사용자 확인).
- [ ] **Step 5: 커밋** — `docs: 푸시 알림 운영·개인정보처리방침`
