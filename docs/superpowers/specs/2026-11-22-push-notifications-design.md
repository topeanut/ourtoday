# 푸시 알림 설계 (2026-11-22)

## 왜

마이페이지에 "앱 푸시 알림은 준비 중이에요"라는 문구와 토글 두 개(작년 오늘 알림, 상대가 기록하면 알림)가 설정값만 저장되고 실제로는 아무것도 보내지 않는다. 사용자(개발자)는 "준비 중"이라고 적힌 기능은 있어야 한다고 판단했고, 커플 일기 앱 특성상 상대의 기록·반응을 바로 알게 되는 것이 둘이 계속 쓰게 만드는 핵심 동기다.

## 목표

- Android·iOS 앱에서 아래 4가지 알림을 실제로 받는다.
- 알림을 누르면 앱이 열리고 해당 화면(웹 경로)으로 이동한다.
- 마이페이지의 "준비 중" 문구를 없애고, 토글이 실제 발송을 제어한다.

## 비목표 (1차에서 안 함)

- 웹 브라우저 푸시(Web Push). 앱 전용.
- "오늘 아직 안 썼어요" 같은 재촉·리마인더 알림 (부담 없는 일기라는 성격과 충돌).
- 알림 목록(인앱 알림함), 배지 숫자 관리.
- 마케팅·공지 푸시.

## 안티 목표 (이렇게 되면 실패)

- 잠금화면에 일기 내용이 노출됨 → 본문은 절대 넣지 않는다. 이름과 행동만.
- 같은 일로 알림이 여러 번 옴 (예: 반응을 껐다 켰다 하면 매번 알림).
- 앱 첫 실행에 맥락 없이 알림 권한부터 물어봄.

## 알림 종류

| 종류 | 설정 키 | 문구 (제목 "오늘의 우리" 고정, 본문) | 시점 | 누르면 |
|---|---|---|---|---|
| 상대가 기록함 | `notifyPartnerPost` (기존) | "{상대 닉네임}이(가) 오늘의 기록을 남겼어요" (기록 날짜가 오늘이 아니면 "{M월 D일}의 기록을 남겼어요") | 기록 생성 직후 | `/posts/{id}` |
| 작년 오늘 | `notifyMemoryToday` (기존) | "1년 전 오늘, 둘이 남긴 기록이 있어요" | 매일 20:00 KST, 정확히 1년 전 같은 날짜에 커플 기록이 1개 이상일 때만 | `/` (홈의 지난 오늘 카드) |
| 내 기록에 반응 | `notifyReaction` (신규, 기본 켜짐) | "{상대 닉네임}이(가) 내 기록에 반응했어요" | 반응 추가 직후 (취소는 알림 없음) | `/posts/{id}` |
| 커플 연결 완료 | 끌 수 없음 | "{상대 닉네임}님과 연결됐어요. 첫 기록을 남겨 보세요" | 상대가 내 초대코드로 합류한 직후, 초대코드를 만든 사람에게만 | `/` |

- 조사(이/가)는 닉네임 마지막 글자 받침으로 고른다.
- 반응 중복 방지 (설계자 판단): 이모지 종류와 상관없이 같은 (반응한 사람, 기록) 조합은 10분에 한 번만 보낸다. 서버 메모리 캐시로 충분 (서버 1대).
- 자기 자신에게는 보내지 않는다.

## 구조

```
[앱 네이티브] FCM 토큰 발급 ──브리지──▶ [웹] POST /api/v1/push-tokens ──▶ [서버] push_tokens 저장
[서버] 이벤트(기록·반응·연결) / 스케줄(20:00) ──Firebase Admin SDK──▶ FCM ──▶ Android / (APNs 경유) iOS
[알림 탭] 네이티브가 data.path 를 받아 웹뷰에서 그 경로를 로드
```

### 발송 채널

- Android·iOS 모두 Firebase Cloud Messaging. iOS 는 Firebase 에 APNs 인증 키(.p8)를 등록해 FCM 이 APNs 로 전달.
- 서버는 `com.google.firebase:firebase-admin` 으로 발송. 서비스 계정 JSON 은 서버 `.env` 에 base64(`FIREBASE_SERVICE_ACCOUNT`)로 넣고 git 에 두지 않는다. 값이 없으면 발송 기능은 꺼진 채로 기동(로컬·테스트).
- 메시지는 notification + data 형태. data 에 `path`(웹 경로), `type`.

### 서버

- 새 테이블 `push_tokens`: id, user_id(FK), token(unique), platform(`android`/`ios`), created_at, updated_at. 한 사용자가 여러 기기 가능.
- `POST /api/v1/push-tokens` {token, platform}: upsert (다른 사용자에 묶인 같은 토큰이면 현재 사용자로 옮김 — 같은 폰에서 계정 바꾼 경우).
- `DELETE /api/v1/push-tokens` {token}: 로그아웃 때 웹이 호출.
- 탈퇴 시 토큰 삭제. 커플 해제 시 토큰은 유지(알림 대상이 없어질 뿐).
- FCM 응답이 `UNREGISTERED`/`INVALID_ARGUMENT`(토큰 무효)면 그 토큰 삭제.
- 발송은 요청 처리와 분리: 트랜잭션 커밋 후(`@TransactionalEventListener(phase = AFTER_COMMIT)`) + `@Async`. 발송 실패가 기록 저장을 실패시키면 안 된다.
- 작년 오늘: `@Scheduled(cron = "0 0 20 * * *", zone = "Asia/Seoul")`. 커플별로 1년 전 날짜 기록 존재 여부 확인 → 설정 켠 두 사람에게 발송. 2월 29일 기록은 평년에는 건너뛴다.
- `user_settings.notify_reaction` 컬럼 추가. 운영은 `ddl-auto: update` 라 NOT NULL 컬럼 추가가 기존 행 때문에 실패하므로 `columnDefinition = "boolean not null default true"` 로 정의.
- 설정 API(`UserSettingsResponse`/`UpdateRequest`)에 `notifyReaction` 추가. 기존 클라이언트가 이 필드 없이 PATCH 해도 깨지지 않게 null 이면 기존 값 유지.

### Android 앱

- Firebase BoM + `firebase-messaging`, `google-services` 플러그인, `app/google-services.json`.
- `FirebaseMessagingService` 구현: 토큰 갱신(onNewToken) 시 웹에 다시 전달, 포그라운드 수신 시 직접 알림 표시(앱이 켜져 있어도 보이게), 알림 채널 "기록 알림" 1개.
- Android 13+ `POST_NOTIFICATIONS` 런타임 권한.
- 알림 탭 → `MainActivity` 인텐트 extra `path` → 우리 도메인 경로만 허용하고 웹뷰 로드 (기존 `onNewIntent` 흐름에 합침).
- 브리지 메시지 추가: `pushPermission`(권한 요청), `pushStatus`(현재 권한·토큰 조회). 네이티브 → 웹 결과는 기존 Apple 로그인처럼 `CustomEvent('ourtoday:push', {detail:{permission, token, platform}})`.
- 버전: versionCode 2 / versionName 1.1.0. 현재 Play 는 "프로덕션 액세스 신청" 심사 중이고 프로덕션 출시는 아직 없다 → 액세스 승인 후 만드는 첫 프로덕션 출시에 이 빌드를 올린다 (비공개 테스트 트랙에도 같은 빌드).

### iOS 앱

- Firebase iOS SDK(SPM, `FirebaseMessaging`), `GoogleService-Info.plist`, Push Notifications capability + `aps-environment`(production), Background Modes 불필요(알림 표시만).
- `UNUserNotificationCenter` 권한 요청은 웹이 `pushPermission` 을 보낼 때만. 포그라운드 수신도 배너로 표시.
- 알림 탭 → `userInfo.path` → 웹뷰 로드(우리 도메인 경로만).
- 버전 1.0.2. 1.0.1(키보드 수정)이 심사 대기 중이면 그대로 두고, 통과 후 1.0.2 를 따로 제출한다 (취소·재제출하지 않음).

### 웹

- `lib/push.ts`: 앱이면 네이티브에 `pushStatus` 요청 → 토큰 오면 서버 등록. 로그인 상태에서 앱 시작 시 1회, 토큰 갱신 이벤트 시 다시.
- 권한 요청 시점 (설계자 판단): 두 번의 자연스러운 순간에만 묻는다.
  1. 초대코드를 만든 직후, 초대 카드 아래 "상대가 들어오면 알려 드릴게요 [알림 받기]" — 만든 사람이 기다리다 앱을 떠나도 '커플 연결 완료' 알림을 받을 수 있게.
  2. 초대코드를 입력해 연결된 사람(또는 1에서 고르지 않은 사람)은 커플 연결 직후 홈에 처음 들어올 때 시트로 이유를 보여 준 뒤 "알림 받기".
  "나중에"면 다시 묻지 않고 마이페이지에서 켤 수 있다. 권한이 이미 허용돼 있으면 묻지 않고 토큰만 등록한다.
- 마이페이지 알림 카드: "준비 중" 문구 삭제. 토글 3개(작년 오늘, 상대 기록, 반응). 기기 권한이 꺼져 있으면 카드 위에 "휴대폰 설정에서 알림이 꺼져 있어요" + [설정 열기](네이티브가 앱 알림 설정 화면을 연다). 웹 브라우저에서는 "알림은 앱에서 받을 수 있어요" 안내만.
- 로그아웃 시 `DELETE /push-tokens`.
- 이전 버전 앱(브리지에 push 메시지가 없는 1.0/1.0.1)에서는 아무것도 하지 않는다 (네이티브 응답이 없으면 조용히 끝).

## 사용자가 직접 할 일

1. Firebase 프로젝트 `ourtoday` 생성 (Google 애널리틱스 끔) — 약관 동의 포함.
2. Apple Developer → Keys 에서 APNs 키 생성(.p8, 다시 받을 수 없음 → `~/.secrets/ourtoday/` 보관) 후 Firebase 에 업로드. 로그인·2단계 인증은 사용자.
3. 서버 `.env` 에 서비스 계정 키 등록 (비밀값 입력은 사용자: 명령은 준비해 줌).
4. 스토어 심사 제출 확인.

## 개인정보처리방침

수집 항목에 "푸시 알림 기기 토큰(알림 발송 목적, 로그아웃·탈퇴 시 삭제)"을 추가하고, 처리 위탁에 Google(Firebase Cloud Messaging)을 추가한다. App Store 개인정보 라벨·Play 데이터 보안 섹션도 같은 내용으로 갱신(사용자 확인 후 제출).

## 테스트

- 서버: 토큰 upsert/삭제, 각 알림 발송 조건(설정 꺼짐·자기 자신·커플 없음·중복 반응 10분), 작년 오늘 대상 계산(2/29 포함), 발송기는 인터페이스로 두고 테스트에선 가짜 구현.
- 실기기: 갤럭시(adb)·아이폰 12 mini 에서 4가지 알림 수신과 탭 이동, 권한 거부 후 설정 안내.

## 빌더에게 맡기는 것

- 알림 아이콘(Android 상태바용 단색 아이콘)은 기존 앱 아이콘에서 단색 실루엣으로 만든다.
- 클래스·패키지 이름, 캐시 구현 방식.
