# 위치 공유 설계 (2026-11-22)

> 두 가지를 다룬다.
> **Part A — 실시간 위치 공유** (백그라운드, 선택 기능. 푸시 기능 구현 완료 후 착수).
> **Part B — 기록에 위치 첨부** (캘린더·게시글. 백그라운드 무관, 지금 바로 착수 가능).

## 왜

"오늘의 우리"를 추억 기록 앱에서 **데이트하는 커플이 서로 지금 어디 있는지 알고, 다녀온 곳을 기록에 남기는 앱**으로 넓힌다. 실시간 공유는 기본 기능이 아니라 **설정에서 켠 커플만 쓰는 선택 기능**이다. 참고 앱은 "도와줘"(kr.jiasoft.helpme) — 평소 표시 없이 조용히 도는 significant-change 방식을 쓴다.

두 사람이 각자 앱을 깔고 자기 계정으로 로그인한 **양방향 커플 앱**이 전제. 한쪽이 상대 몰래 추적하는 구조가 아니라, 각자 자기 공유를 켜고 서로 본다.

---

# Part A — 실시간 위치 공유

## 목표

- 설정에서 **위치 공유를 켠** 커플은 서로의 위치를 지도에서 본다. 끈 사람 위치는 안 보인다.
- 평소엔 **느슨 모드**(큰 이동만, 시간당 몇 번)로 조용히 경로를 쌓는다 → iOS 파란 바·Android 상주 알림 **안 뜬다**.
- **"정확한 위치 보기" ON/OFF 버튼**을 누르면 상대 폰이 잠깐 정밀 모드로 올라가 분 단위 실시간을 보여준다. **3분 뒤 자동으로 꺼지고** 느슨으로 복귀. 화면에 남은 시간 타이머 표시.
- **경로 요약은 영구 보관**(캘린더 날짜에서 "이날 다닌 길"). 원본 좌표는 90일 후 삭제.

## 비목표 (1차에서 안 함)

- 웹 브라우저 위치 공유(앱 전용). 지오펜스 "집 도착/출발" 알림·SOS. WebSocket 스트리밍(폴링으로 충분). 위치 통계·히트맵. 3인 이상.

## 안티 목표 (이렇게 되면 실패)

- 상대가 모르는 채로 수집됨 → 양쪽이 각자 명시적으로 켜야만 공유. 상태는 양쪽 설정에서 늘 확인 가능.
- 평소에 OS 표시가 떠서 "감시당하는 느낌" → 평소(느슨) 표시 없음. 정밀은 버튼 누른 3분만.
- 위치 포인트 무한 적재로 디스크 압박 → 원본 raw 90일, 일자 요약 영구.
- 배터리를 눈에 띄게 먹음 → 느슨은 significant-change(하루 1~5%), 정밀은 3분 제한.

## 동작 요약

```
평소 (느슨, 양쪽 폰): significant-change 콜백마다 1점 업로드. 표시 없음. 신선도 수십 분~1시간.
위치 탭(상대가 공유 켰을 때만 나에게 보임): 상대 "대략 위치 + N분 전" + 오늘 경로.
[정확한 위치 보기] ON:
  내 폰 → 서버 "정밀 요청(만료 3분)" → 상대 폰에 고우선순위 무음 푸시
  → 상대 폰 정밀 모드(3분 타이머), 수~십수 초마다 정밀 업로드
  → 내 화면 폴링 5~10초로 실시간 + 남은 시간 타이머
  3분 경과/OFF → 상대 느슨 복귀 (iOS 파란 바 숨김 / Android 상주 알림 3분)
```

## 선택한 접근 (방식 3: 혼합 + 버튼 방아쇠)

고려한 셋: ①느슨만(도와줘식, 표시 없음·실시간 약함) ②정밀 연속+WebSocket(진짜 실시간·표시 항상·과함) ③**혼합: 평소 느슨 + 버튼 3분 정밀(선택)**.

**③을 택한 이유**: 사용자가 ①평소 표시 없음을 최우선, ②그래도 "지금 콕" 정밀 실시간을 원함. 느슨 기본 + 명시적 버튼 방아쇠가 둘을 동시에 만족하는 유일 구조. 탭 진입만으론 정밀이 안 켜지고 버튼이 방아쇠.

## 결정된 사항 (사용자 합의)

- 정밀: **버튼 ON/OFF**, 켜면 **3분 타이머** 후 자동 OFF, 남은 시간 표시.
- 느슨 모드: **표시 없음 우선**(= Foreground Service 없이, 시간당 몇 번). 배터리 하루 1~5%. 신선도 수십 분~1시간 수용.
- **iOS 파란 바: 기본 숨김**(`showsBackgroundLocationIndicator=false`, Always 권한). **App Review 반려 시에만 표시로 폴백.**
- Android 정밀 3분간 상주 알림 뜨는 것: **수용**.
- 원본 raw **90일**, 일자 요약 **영구**.
- 지도 SDK: **카카오맵**(이미 카카오 로그인 사용, 일 30만 쿼터 내 무료).
- **위치 탭 노출: 상대가 공유를 켰을 때만 나에게 보임.** 안 켰으면 탭 없음. "내 공유 켜기" 토글은 마이페이지에 상시.
- iOS 버튼 깨우기는 **best-effort**(무음 푸시 OS throttle·앱 종료 시 지연·실패 가능). 100% 즉시 보장 아님 — 수용.
- **선행 의존**: 이 Part A 구현은 **푸시 기능 구현 완료 후 착수**(FCM 토큰·Firebase Admin 발송 공유).

## 구조

### 위치 수집 모드 (네이티브)

| 모드 | 방식 | 주기 | 표시 |
|---|---|---|---|
| 느슨 (평소) | iOS `startMonitoringSignificantLocationChanges` / Android geofence·passive·저빈도 (**FGS 없음**) | 큰 이동 시, 시간당 몇 번 | 없음 |
| 정밀 (버튼 3분) | iOS `startUpdatingLocation`(`showsBackgroundLocationIndicator=false`) / Android **Foreground Service** + 고빈도 | 수~십수 초 | iOS 없음(반려 시 파란 바) / Android 상주 알림 |

- 공유 ON이면 느슨 시작, 앱 닫아도 OS 콜백으로 유지. Android는 `BOOT_COMPLETED`로 재가동.
- 정밀 전환 신호는 **서버가 소유**(정밀 요청 만료 시각). 상대 ON → 서버 기록 → **고우선순위 data FCM**으로 상대 폰 깨움(Android 12+ 백그라운드 FGS 시작 허용 창 확보). 앱이 꺼져도 서버 만료 기준 자동 종료.

### 서버 (기존 `couple/`·`calendar/` 컨벤션 따름: entity+controller+service+repository+dto record)

- `location_shares`: user_id(PK, FK), enabled, updated_at. 느슨 가동 여부 소스 오브 트루스.
- `location_points`: id, user_id(FK), lat, lng, accuracy, captured_at, source(`coarse`/`precise`), created_at. 인덱스 (user_id, captured_at). **raw 90일 후 삭제(스케줄러).**
- `location_precise_requests`: id, requester_id, target_id, expires_at, created_at. 활성 = `expires_at > now`.
- `location_daily`(영구): user_id, date(KST), point_count, first_at, last_at, 단순화 polyline(Douglas-Peucker).
- `location_access_log`: viewer_id, target_id, kind(`coarse`/`precise`), started_at. **정밀 세션 1건 단위로 기록**(폴링마다 X). 위치정보법 제공 기록용.
- API (`/api/v1`, JWT, `@RequireCouple` 또는 Service에서 커플 검증):
  - `PUT /location/sharing` {enabled} / `GET /location/sharing` (내·상대 상태)
  - `POST /location/points` {points:[...]} — 네이티브 배치 업로드. 공유 꺼짐이면 거부. **일부 구간만 OFF였던 배치는 OFF 이후 포인트만 버리고 나머지 수용.**
  - `GET /location/partner` — 상대 최신 1점 + 공유 상태 + 활성 정밀 여부
  - `POST /location/precise` (만료 3분, 고우선순위 무음 푸시, 멱등=활성이면 연장) / `DELETE /location/precise` (즉시 만료 + off 푸시)
  - `GET /location/track?date=YYYY-MM-DD` — 요약 polyline(없으면 raw)
- 발송은 푸시 설계의 `push_tokens`·Firebase Admin 재활용. data `type=location_precise_on|off`, `expiresAt`. **고우선순위**.
- 커플 해제·탈퇴: 공유 OFF, 포인트·요청 삭제, 정지용 무음 푸시(best-effort). 자기 자신엔 요청 안 보냄.

### Android 앱 (현재 매니페스트엔 위치/FGS/BOOT 권한 전무 → 전부 신규)

- 권한: `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION`(설정에서 "항상 허용" 별도 승인) + 13+ `POST_NOTIFICATIONS`(정밀 FGS 알림용).
- 느슨: `FusedLocationProviderClient` 저빈도 + geofence/passive, **FGS 없이**(시간당 몇 번). `BOOT_COMPLETED` 리시버.
- 정밀: **고우선순위** 무음 FCM(`location_precise_on`) 수신 → Foreground Service(알림 "위치 공유 중") 시작, 고빈도 업로드, 3분 타이머/`off` 수신 시 종료.
- 브리지 추가(`NativeBridge.kt`): `locationShare`(start/stop), `locationStatus`. 네이티브→웹 `CustomEvent('ourtoday:location', {...})`.
- 업로드: 웹뷰 닫혀도 돌아야 하므로 **네이티브가 직접 서버 호출** → 네이티브가 JWT 보관/갱신 필요(아래 공통 참조).

### iOS 앱 (현재 Info.plist/entitlements에 위치·백그라운드·aps 키 없음 → 신규)

- `CLLocationManager`, `NSLocationAlwaysAndWhenInUseUsageDescription`+`WhenInUse`. **"앱 사용 중" 먼저 → 이후 "항상"으로 업그레이드 유도**(iOS 13+는 첫 프롬프트 Always 직접 불가).
- Background Modes: **Location updates + Remote notifications**(무음 푸시 깨우기용). entitlements에 `aps-environment` 추가. (푸시 설계가 "Background Modes 불필요"라 한 건 알림표시 전용 범위였고, 위치는 이 모드가 필요 — 모순 아님, 위치가 추가하는 것.)
- 느슨: `startMonitoringSignificantLocationChanges`(+필요 시 `startMonitoringVisits`).
- 정밀: content-available 푸시 수신 → `startUpdatingLocation`, `showsBackgroundLocationIndicator=false`, 3분/off에 `stopUpdatingLocation`. 백그라운드 URLSession 업로드.
- 브리지(`WebViewController.swift`): `locationShare`, `locationStatus` → `ourtoday:location`.
- **best-effort 한계 명시**: 무음 푸시 OS throttle(시간당 2~3회)·앱 강제종료 시 전달 보장 없음. significant-change 기동을 보조 채널로.

### 웹

- 새 "위치" 화면(진입점은 상대 공유 ON일 때만 노출): 카카오맵(상대 ●/나 ●), 상대 "대략 위치 + N분 전", **[정확한 위치 보기] ON/OFF + 남은 시간 타이머**, 오늘 경로.
- `lib/location.ts`: `locationStatus`/`locationShare` 브리지, `GET /location/partner` 폴링(느슨 20~30초, 정밀 활성 5~10초), `ourtoday:location` 수신.
- 마이페이지: "위치 공유" 토글(기본 꺼짐) + "상대도 켜야 서로 보여요" + 권한 꺼짐 시 설정 열기. 공유 중 은근한 상태 표시.
- 이전 버전 앱(브리지에 location 없음)은 조용히 미동작.

## 공통 — 네이티브의 JWT 획득 (신규, Part A 필수)

- 토큰은 현재 웹 localStorage(`ourtoday.token`/`ourtoday.refresh`, `web/src/lib/auth.ts`)에 있고 웹이 Bearer로 전송(`api.ts`). 네이티브는 접근 수단 0.
- 백그라운드 업로드를 위해: 로그인/갱신 시 **웹→네이티브로 토큰 전달(신규 브리지)**, 네이티브 보안 저장(iOS Keychain / Android EncryptedSharedPreferences), 네이티브 독립 refresh(`AuthController.refresh`는 상태 없는 재발급이라 웹/네이티브 병행 OK). **서버가 refresh 회전/폐기 도입하면 재설계 필요** — 그 전까지는 안전.

## 사용자가 직접 할 일 (Part A, 출시 전)

1. iOS "항상 허용" + 파란 바 숨김에 대한 App Review 소명 제출. **반려 시 파란 바 표시로 폴백.**
2. Google Play 백그라운드 위치 선언서 + 시연 영상.
3. **위치기반서비스사업자 신고**(방통위) — 타인 위치 제공 서비스 법적 요건.
4. 개인정보처리방침·App Store 라벨·Play 데이터 보안에 "정밀/대략 위치(상대 공유 목적, 커플 해제·탈퇴 시 삭제)" + 위치 처리 위탁 추가.

---

# Part B — 기록에 위치 첨부 (백그라운드 무관, 즉시 착수 가능)

## 목표

- **캘린더 메모**(`CalendarMemo`)와 **게시글/일기**(`Post`)에 **장소를 선택적으로 붙인다.**
- 위치는 ①**현재 위치 자동**(한 번 찍기) ②**지도에서 직접 콕 찍기** 둘 다. (설계자 판단, 카카오맵 이미 쓰므로 둘 다 쉬움.)
- 저장 항목: lat, lng, place_name(선택, 카카오 장소명/주소 역지오코딩). 기록 상세·캘린더에 지도 핀 또는 장소명 표시.

## 구조

- **포그라운드 1회 위치만** 사용. `navigator.geolocation.getCurrentPosition()` — 백그라운드·파란 바·네이티브 수정 **불필요**. (앱 권한이 없으면 "지도에서 찍기"로 대체.)
- 서버: `calendar_memos`·`posts`에 nullable 컬럼 `place_lat`, `place_lng`, `place_name` 추가(`ddl-auto: update`, nullable이라 기존 행 안전). 각 Create/Update DTO·Response에 선택 필드 추가(없이 와도 깨지지 않게).
- 웹: 작성 화면에 "위치 추가" — 현재 위치 버튼 + 카카오맵 장소 검색/핀. 상세·캘린더에서 핀/장소명 표시, 탭하면 지도.
- 역지오코딩: 카카오 로컬 API(좌표→주소/장소). 키는 카카오맵과 공유.

## 사용자가 직접 할 일 (Part B)

- 카카오 개발자 콘솔에서 지도/로컬 API 활성화(기존 카카오 앱 키 재사용 가능 여부 확인).
- 개인정보처리방침에 "기록에 첨부한 위치(이용자가 직접 입력/선택)" 추가.

---

## 테스트

- 서버(A): 공유 on/off 접근 제어(꺼진 사람 조회 거부), 포인트 배치(부분 OFF 처리), 정밀 요청 생성·만료·멱등·off, 일자 요약 집계, raw 90일 정리, 접근 로그 세션 단위, 커플 아닌 사용자 차단.
- 서버(B): 메모·게시글 위치 CRUD, 위치 없는 요청 하위호환.
- 실기기(A): 갤럭시(adb)·아이폰 — 느슨 백그라운드 수집(앱 종료 후 이동), 버튼 ON→상대 정밀 전환·3분 자동 OFF, iOS 파란 바 안 뜸(반려 폴백 분기), Android 상주 알림 뜸/사라짐, 공유 OFF 즉시 사라짐, 무음 푸시 유실 시 거동.
- 실기기(B): 현재 위치 첨부·지도 찍기·역지오코딩 표시.
- 배터리(A): 느슨 반나절 소모(목표 1~5%/일).

## 빌더에게 맡기는 것

- 클래스·패키지·파일 이름, polyline 단순화 임계치, 폴링 간격 미세값.
- 네이티브 JWT 보관·갱신 세부(기존 토큰 저장 위치에 맞춤).
- 카카오맵 마커·경로선·핀 스타일, 타이머 UI, "위치 추가" UI 배치.
- 느슨 모드 Android 세부(geofence vs passive vs 저빈도 periodic 배터리·정확도 균형, 시간당 몇 번 목표).

## 열린 리스크 (코드 전 인지)

- **iOS 무음 푸시 깨우기 신뢰성**: OS throttle·앱 종료 시 지연/실패. best-effort로 수용됨. 실측 후 심하면 UX에 "잠시 후 갱신" 안내 보강.
- **Android 느슨 FGS 없이**: 백그라운드 위치 강한 제한으로 시간당 몇 번만. "대략 동네"엔 충분, 촘촘함은 정밀 버튼이 담당.
- **푸시 선행 의존**: Part A는 푸시 완료까지 대기. Part B는 선행 의존 없음.
