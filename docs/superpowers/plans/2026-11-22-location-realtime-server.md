# 실시간 위치 공유 — 서버 토대 (Part A-1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 커플이 서로 위치를 공유/조회하는 백엔드(스키마·REST API·무음푸시 확장)를 만든다. 웹·네이티브(별도 계획)가 이 API 위에 올라간다.

**Architecture:** 기존 `couple/`·`calendar/` 도메인 컨벤션(entity+controller+service+repository+dto record, UUID 컬럼·연관관계 없음)으로 새 `location/` 도메인을 추가. 느슨 가동 여부는 `location_shares`가 소스 오브 트루스. 정밀 모드는 `location_precise_requests`의 만료 시각으로 서버가 권위를 가지며, 전환 신호는 기존 푸시 인프라를 **data-only·고우선순위**로 확장해 보낸다.

**Tech Stack:** Spring Boot 3 / Java 21 / JPA / H2(test)·PostgreSQL(prod), Firebase Admin(기존 `push/`), `@Scheduled`.

**Spec:** `docs/superpowers/specs/2026-11-22-location-sharing-design.md` (Part A, 서버 절)

## Global Constraints

- 공유는 각자 켬: 내 위치는 내 `location_shares.enabled=true`일 때만 상대에게 보인다. 끄면 즉시 조회에서 사라진다.
- 같은 커플만 조회·조작(`coupleService.getConnectedCoupleId` + 상대 검증). 자기 자신에겐 정밀 요청/푸시를 보내지 않는다.
- `POST /location/points`: 공유 꺼진 사용자 요청 거부. 한 배치 안에서는 **공유 ON/OFF 이력(`location_share_events`)을 참조해, 각 포인트의 captured_at 시점에 공유가 ON이던 포인트만 저장**하고 OFF 구간 포인트는 버린다.
- 정밀 요청 만료 = 3분(`expires_at = now + 3min`), 활성 = `expires_at > now`. 멱등: 활성 중 재요청이면 만료 시각 연장.
- raw(`location_points`)는 captured_at 90일 경과분 스케줄 삭제. `location_daily` 요약은 영구.
- 무음(data-only) 푸시: 알림 표시 없음, 고우선순위(Android FCM `priority=high`, iOS `content-available=1` + `apns-priority=10`), data `type=location_precise_on|off`, `expiresAt`.
- 위치정보법 로그: 정밀 세션 **1건당 1행**(`POST /location/precise` 성공 시) `location_access_log` 기록. 폴링(GET)마다 기록하지 않는다.
- 좌표 검증: lat `-90~90`, lng `-180~180`, accuracy ≥ 0. DTO record + `@Valid`.
- 에러 형식 `$.error.code`(`API-CONVENTIONS.md`). 새 에러코드 필요 시 `API.md` 먼저(단 API.md는 gitignored → 커밋 제외, schema.dbml·api/http/api.http만 추적).
- 린트/빌드: `cd api && ./gradlew build` (환경변수 불필요, 테스트는 H2).

## Review Focus

1. 공유 끈 사용자의 위치를 상대가 조회 → 안 보여야(최신점 null, sharing=false) → Task 2 `공유를_끄면_상대에게_안_보인다`.
2. 남의 커플 사용자가 `/location/partner`·`/location/precise` 호출 → 거부(FORBIDDEN/빈결과) → Task 4·5 `커플이_아니면_거부한다`.
3. 정밀 요청 활성 중 재요청 → 중복 생성 없이 만료 연장(멱등) → Task 5 `활성_중_재요청은_만료를_연장한다`.
4. 배치가 공유 OFF 토글을 걸침 → 각 포인트 captured_at 시점에 ON이던 것만 저장, OFF 구간 포인트는 버림 → Task 3 `OFF_구간_포인트만_버리고_ON_구간은_저장한다`.
5. 자기 자신에게 정밀 요청/푸시 안 감, 토큰 없는 상대도 500 안 나고 처리 → Task 5 `자기자신엔_안보내고_토큰없어도_실패안함`.

---

### Task 1: 무음(data-only) 고우선순위 푸시 확장

**Files:**
- Modify: `api/src/main/java/com/ourtoday/api/push/PushSender.java` (메서드 추가), `FcmPushSender.java`, `NoopPushSender.java`, `PushDispatcher.java`
- Create: `api/src/main/java/com/ourtoday/api/push/DataPush.java` (record)
- Test: `api/src/test/java/com/ourtoday/api/push/DataPushSenderTest.java`

**Interfaces:**
- Consumes: 기존 `PushSender`, `FcmPushSender`, `PushDispatcher`, `PushTokenRepository`.
- Produces:
  - `record DataPush(String type, Map<String,String> data)` (알림 본문 없음).
  - `PushSender.sendData(String token, DataPush push): SendResult` — FCM data-only, 고우선순위, iOS `content-available`.
  - `PushDispatcher.sendDataToUsers(Collection<UUID> userIds, DataPush push)` — 토큰 조회·무효토큰 정리는 기존 `sendToUsersNow`와 동일 패턴 재사용.

- [ ] **Step 1: 실패 테스트** — `DataPushSenderTest`(가짜 `FirebaseMessaging` 또는 NoopPushSender 기반): `sendData`가 Notification 없이 data(type, expiresAt)만 담고 OK 반환; 무효 토큰이면 INVALID_TOKEN. (FcmPushSender는 FirebaseMessaging mock으로 빌더 호출 검증, 또는 메시지 조립을 분리해 단위 검증.)
- [ ] **Step 2: 실패 확인** — `cd api && ./gradlew test --tests DataPushSenderTest` → FAIL.
- [ ] **Step 3: 구현** — `DataPush` record. `PushSender`에 `sendData` 추가. `FcmPushSender.sendData`: `Message.builder().setToken().putAllData(push.data()).putData("type",...)` + `AndroidConfig.builder().setPriority(HIGH)` + `ApnsConfig`에 `Aps.builder().setContentAvailable(true)` + header `apns-priority=10`, `apns-push-type=background`. **Notification/alert 미설정.** `NoopPushSender.sendData`도 구현(로그). `PushDispatcher.sendDataToUsers`.
- [ ] **Step 4: 통과 확인** — PASS.
- [ ] **Step 5: 커밋** — `git add -A && git commit -m "feat(api): 무음(data-only) 고우선순위 푸시 발송 추가"`

---

### Task 2: 위치 공유 토글 (`location_shares`)

**Files:**
- Create: `api/src/main/java/com/ourtoday/api/location/LocationShare.java`, `LocationShareRepository.java`, `LocationShareEvent.java`, `LocationShareEventRepository.java`, `LocationService.java`, `LocationController.java`, `dto/SharingRequest.java`, `dto/SharingResponse.java`
- Test: `api/src/test/java/com/ourtoday/api/location/LocationSharingFlowTest.java`
- Modify: `schema.dbml`, `api/http/api.http`

**Interfaces:**
- Consumes: `coupleService.getConnectedCoupleId`, 상대 조회(없으면 Task 2에서 `coupleService.getPartnerId(userId): UUID` 추가 — `CoupleMember`에서 같은 couple의 다른 user).
- Produces:
  - `LocationShare`: user_id(PK), enabled(boolean), updated_at. (현재 상태)
  - `LocationShareEvent`: id, user_id, enabled(boolean), at(Instant). **토글마다 1행 append**(이력). 인덱스 (user_id, at).
  - `SharingResponse(boolean mine, boolean partner)`.
  - `PUT /location/sharing` {enabled} → 200 `SharingResponse`. 상태가 실제로 바뀔 때만 이벤트 append(같은 값 PUT 은 이벤트 안 남김). `GET /location/sharing` → 200 `SharingResponse`.
  - `LocationService.isSharing(UUID userId): boolean` (현재 상태).
  - `LocationService.wasSharingAt(UUID userId, Instant at): boolean` — 그 시각 ≤ 최신 이벤트로 ON/OFF 판정(이벤트 없으면 false). Task 3 이 씀.

- [ ] **Step 1: 실패 테스트** — `LocationSharingFlowTest`: `공유를_켜고_끌_수_있다`(PUT true→GET mine=true; PUT false→mine=false), `상대_공유_상태가_보인다`(상대가 켜면 partner=true), `공유를_끄면_상대에게_안_보인다`(GET sharing partner=false), `토글하면_이벤트가_쌓인다`(ON→OFF→ON 후 이벤트 3행, 같은 값 중복 PUT 은 안 쌓임), `커플이_아니면_거부한다`(솔로 유저 → `COUPLE_REQUIRED`).
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests LocationSharingFlowTest` → FAIL.
- [ ] **Step 3: 구현** — 엔티티(share + event)·레포·서비스·컨트롤러·DTO. `coupleService.getPartnerId` 추가. 공유 레코드 upsert + 상태 변경 시 이벤트 append. `wasSharingAt`: `findTopByUserIdAndAtLessThanEqualOrderByAtDesc(userId, at)` 의 enabled(없으면 false).
- [ ] **Step 4: 통과 확인** — PASS.
- [ ] **Step 5: 문서** — `schema.dbml`(location_shares)·`api.http`.
- [ ] **Step 6: 커밋** — `git commit -m "feat(api): 위치 공유 토글"`

---

### Task 3: 위치 포인트 배치 업로드 + 90일 정리 (`location_points`)

**Files:**
- Create: `location/LocationPoint.java`, `LocationPointRepository.java`, `dto/PointsUploadRequest.java`, `dto/PointInput.java`, `location/LocationCleanupScheduler.java`
- Modify: `LocationService.java`(upload), `LocationController.java`(POST), `schema.dbml`, `api/http/api.http`
- Test: `api/src/test/java/com/ourtoday/api/location/LocationPointsFlowTest.java`

**Interfaces:**
- Produces:
  - `LocationPoint`: id, user_id, lat(double), lng(double), accuracy(Double null), captured_at(Instant), source(enum `COARSE`/`PRECISE`), created_at. 인덱스 (user_id, captured_at).
  - `record PointInput(@DecimalMin/Max lat, lng, Double accuracy, @NotNull Instant capturedAt, @NotNull PointSource source)`.
  - `POST /location/points` {points:[PointInput]} → 204. 현재 공유 OFF면 `FORBIDDEN`. 공유 ON이면 각 포인트를 `wasSharingAt(userId, capturedAt)` 로 걸러 **ON이던 포인트만 저장**(OFF 구간 포인트는 조용히 버림).
  - `LocationPointRepository.findTopByUserIdOrderByCapturedAtDesc(userId)` (Task 4용), `deleteByCapturedAtBefore(Instant)` (정리용).

- [ ] **Step 1: 실패 테스트** — `LocationPointsFlowTest`: `공유_켜면_포인트를_올린다`(POST 2점 → 204, 2점 저장), `공유_OFF면_거부`(현재 sharing false → 403), `OFF_구간_포인트만_버리고_ON_구간은_저장한다`(공유 켬 → 끔 → 다시 켬으로 이벤트 이력을 만들고, captured_at 이 OFF 구간인 점과 ON 구간인 점을 섞은 배치 업로드 → ON 구간 점만 저장되고 OFF 구간 점은 버려짐. 이벤트 시각은 고정 Instant 로 제어), `범위_밖_좌표_거부`(lat 100 → 400).
- [ ] **Step 2: 실패 확인** — FAIL.
- [ ] **Step 3: 구현** — 엔티티·레포·DTO·서비스 upload(현재 공유 OFF면 403; ON이면 `wasSharingAt` 로 포인트 필터 후 saveAll)·컨트롤러. `LocationCleanupScheduler`: `@Scheduled(cron="0 30 4 * * *", zone="Asia/Seoul")` → `deleteByCapturedAtBefore(now-90d)`.
- [ ] **Step 4: 통과 확인** — PASS.
- [ ] **Step 5: 문서** — schema.dbml·api.http.
- [ ] **Step 6: 커밋** — `git commit -m "feat(api): 위치 포인트 배치 업로드 + 90일 정리"`


---

### Task 4: 상대 위치 조회 (`GET /location/partner`)

**Files:**
- Modify: `LocationService.java`, `LocationController.java`, `dto/PartnerLocationResponse.java`(신규)
- Test: `LocationPartnerFlowTest.java`

**Interfaces:**
- Consumes: Task 2 `getPartnerId`·`isSharing`, Task 3 `findTopByUserIdOrderByCapturedAtDesc`, Task 5 활성 정밀 여부(없으면 이 Task에선 false 고정, Task 5에서 연결).
- Produces: `GET /location/partner` → `PartnerLocationResponse(boolean partnerSharing, LatLng latest /*null 가능*/, Instant capturedAt, boolean preciseActive)`.

- [ ] **Step 1: 실패 테스트** — `LocationPartnerFlowTest`: `상대가_공유하고_포인트있으면_최신점을_준다`, `상대가_공유안하면_null과_false`, `공유를_끄면_상대에게_안_보인다`(상대 켰다가 끄면 latest=null, partnerSharing=false), `커플이_아니면_거부한다`.
- [ ] **Step 2~4**: FAIL → 구현(상대 공유 검증 후 최신점) → PASS.
- [ ] **Step 5: 커밋** — `git commit -m "feat(api): 상대 위치 조회"`

---

### Task 5: 정밀 요청/해제 + 무음푸시 + 접근로그 (`location_precise_requests`, `location_access_log`)

**Files:**
- Create: `location/PreciseRequest.java`, `PreciseRequestRepository.java`, `location/LocationAccessLog.java`, `LocationAccessLogRepository.java`
- Modify: `LocationService.java`, `LocationController.java`
- Test: `LocationPreciseFlowTest.java`
- Modify: schema.dbml, api.http

**Interfaces:**
- Consumes: Task 1 `PushDispatcher.sendDataToUsers`, Task 2 `getPartnerId`, `Clock`(만료·테스트 — PostService처럼 주입).
- Produces:
  - `PreciseRequest`: id, requester_id, target_id, expires_at, created_at.
  - `POST /location/precise` → 204. 상대에게 `DataPush("location_precise_on", {expiresAt})` 고우선순위 발송. 활성(expires_at>now) 중이면 만료만 연장(멱등). 성공 시 `location_access_log`(viewer=requester, target, kind=PRECISE, started_at) 1행.
  - `DELETE /location/precise` → 204. 내가 건 요청 즉시 만료 + `DataPush("location_precise_off")`.
  - `LocationService.hasActivePreciseRequest(targetUserId): boolean` — Task 4의 preciseActive·Task 3 네이티브 판단이 씀(활성 요청의 target 기준).

- [ ] **Step 1: 실패 테스트** — `LocationPreciseFlowTest`(가짜 PushDispatcher로 호출 검증, Clock 고정): `정밀_요청하면_상대에게_무음푸시_간다`(발송기에 on 1회 + access_log 1행 + partner preciseActive=true), `활성_중_재요청은_만료를_연장한다`(요청 2회 → row 1개, 만료 연장, 푸시는 매번 또는 1회 — **설계자 판단: 매 요청마다 push(네이티브 깨우기 신뢰성)**), `해제하면_즉시_만료되고_off_푸시`(DELETE → preciseActive=false + off 푸시), `만료되면_자동_비활성`(Clock +3min → preciseActive=false), `자기자신엔_안보내고_토큰없어도_실패안함`(상대 토큰 0개여도 204), `커플이_아니면_거부한다`.
- [ ] **Step 2~4**: FAIL → 구현 → PASS.
- [ ] **Step 5: 문서/커밋** — schema.dbml·api.http; `git commit -m "feat(api): 정밀 위치 요청/해제 + 무음푸시 + 접근로그"`

---

### Task 6: 일자 요약 + 경로 조회 (`location_daily`, `GET /location/track`)

**Files:**
- Create: `location/LocationDaily.java`, `LocationDailyRepository.java`, `location/DailyAggregator.java`(스케줄), `dto/TrackResponse.java`
- Modify: `LocationService.java`, `LocationController.java`, schema.dbml, api.http
- Test: `LocationTrackFlowTest.java`

**Interfaces:**
- Produces:
  - `LocationDaily`(영구): user_id, date(LocalDate, KST), point_count, first_at, last_at, polyline(String, 단순화 좌표열 — 예 `"lat,lng;lat,lng"`).
  - `GET /location/track?date=YYYY-MM-DD` → `TrackResponse(List<LatLng> path, int pointCount)`. 요약 있으면 polyline, 없으면 그 날짜 raw를 KST로 조회.
  - `DailyAggregator`: `@Scheduled`(매일 새벽, 전일 집계) — 커플 공유자별 전일 points → Douglas-Peucker 단순화(임계치 빌더) → upsert.

- [ ] **Step 1: 실패 테스트** — `LocationTrackFlowTest`: `요약이_있으면_polyline을_준다`, `요약이_없으면_raw를_준다`(그 날짜 포인트들), `상대_공유여부_검증`(상대 비공유면 거부/빈). 집계기 단위: `전일_포인트를_요약으로_만든다`(간단 polyline), `점이_없으면_요약_안만든다`.
- [ ] **Step 2~4**: FAIL → 구현(단순화 알고리즘은 빌더 선택, 임계치 상수) → PASS.
- [ ] **Step 5: 문서/커밋** — `git commit -m "feat(api): 위치 일자 요약 + 경로 조회"`

---

### Task 7: 커플 해제·탈퇴 시 위치 정리

**Files:**
- Modify: `LocationService.java`(cleanup 메서드), 커플 해제/탈퇴 처리부(`CoupleService`·`AccountController` 흐름에 훅 — 기존 이벤트/호출 지점 확인 후 연결)
- Test: `LocationCleanupFlowTest.java`

**Interfaces:**
- Consumes: 커플 해제/탈퇴 시점(기존 코드에서 포인트/반응 삭제하는 지점과 같은 자리).
- Produces: `LocationService.purgeFor(UUID userId)` — 공유 OFF, 포인트·정밀요청 삭제, 상대에게 off 무음푸시(best-effort). 일자 요약은 본인 것 유지(탈퇴 시 삭제).

- [ ] **Step 1: 실패 테스트** — `LocationCleanupFlowTest`: `탈퇴하면_내_위치데이터가_지워진다`, `커플_해제하면_공유_꺼지고_상대에게_off`.
- [ ] **Step 2~4**: FAIL → 기존 해제/탈퇴 흐름에 `purgeFor` 연결 → PASS.
- [ ] **Step 5: 커밋** — `git commit -m "feat(api): 커플 해제·탈퇴 시 위치 데이터 정리"`

---

## 사용자가 직접 할 일 (이 계획 범위 밖, 출시 전)

- 위치기반서비스사업자 신고(방통위), 개인정보처리방침에 위치(정밀/대략, 상대 공유 목적·해제 시 삭제) + 처리위탁 갱신.
- Firebase가 data-only 고우선순위/백그라운드 푸시를 보내도록 서버 `.env` 설정은 기존 푸시와 동일(추가 없음).

## 빌더에게 맡기는 것

- 클래스·패키지 세부명, polyline 단순화 알고리즘·임계치, 인덱스 미세.
- `getPartnerId` 구현(CoupleMember에서 같은 couple의 다른 user 조회).
- FcmPushSender data-only 메시지의 정확한 APNs 헤더(`apns-push-type=background`, `apns-priority=10`) 지정.

## 후속 계획 (별도 문서)

- **Part A-2 웹**: 위치 화면(카카오맵, 공유 토글, 상대 위치, 정밀 ON/OFF+타이머, 경로), `lib/location.ts` 폴링.
- **Part A-3 iOS 네이티브**: significant-change 느슨 + 정밀 + 무음푸시(content-available) 깨우기 + 브리지 + JWT 보관. (+ 키보드 overlap ~20pt 틈 네이티브 수정 동승.)
- **Part A-4 Android 네이티브**: geofence/저빈도 느슨 + 정밀 FGS + FCM data 수신 깨우기(고우선순위) + 브리지 + JWT.
