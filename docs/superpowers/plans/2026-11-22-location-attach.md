# 기록에 위치 첨부 (Part B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 게시글(3줄 일기)과 캘린더 메모에 장소(좌표+장소명)를 선택적으로 붙이고, 상세·캘린더에서 지도 핀/장소명으로 본다.

**Architecture:** `posts`·`calendar_memos` 엔티티에 nullable 위치 컬럼 3개(`place_lat`, `place_lng`, `place_name`)를 더하고, 각 Create/Update/Response DTO와 Service 매핑에 선택 필드를 추가(기존 패턴 그대로, 연관관계 없이 컬럼). 웹은 카카오맵 SDK 로더와 "위치 추가" 컴포넌트(현재 위치 `getCurrentPosition` + 지도에서 찍기 + 카카오 로컬 역지오코딩)를 만들어 `PostForm`과 캘린더 작성 화면에 끼우고, 상세·캘린더에 핀/장소명을 표시한다.

**Tech Stack:** Spring Boot 3 / Java 21 / JPA / H2(test) · PostgreSQL(prod), Next.js App Router + SWR + Tailwind, 카카오맵 JS SDK + 카카오 로컬 REST API

**Spec:** `docs/superpowers/specs/2026-11-22-location-sharing-design.md` (Part B)

## Global Constraints

- 위치는 **선택**. 세 값은 **함께 있거나 함께 없거나**(좌표만·장소명만 불가). `place_lat`/`place_lng`/`place_name` 모두 nullable.
- 위도 `-90~90`, 경도 `-180~180` 범위 검증. `place_name` 최대 100자.
- nullable 컬럼이라 운영 `ddl-auto: update` 안전(기존 행 영향 없음). 엔티티는 연관관계 없이 컬럼(기존 패턴).
- 같은 커플만 조회, 수정·삭제는 작성자 본인만(`FORBIDDEN`) — 기존 Service 가드 재사용.
- 위치 없이 온 요청(기존 클라이언트)도 깨지지 않게 — 세 필드 생략 시 위치 없음으로 저장.
- 역지오코딩은 카카오 로컬 REST API(좌표→주소/장소명). 키는 카카오맵과 공유, 일 30만 쿼터 내 무료.
- UI 이모티콘 문자(📍 등) 금지 — `web/src/components/Icons.tsx` SVG 스타일만.
- 에러 형식 `$.error.code` (`API-CONVENTIONS.md`). DTO 는 record + validation.
- 수정 후 린트: 백엔드 `cd api && ./gradlew build`, 웹 `cd web && npm run lint`.

## Review Focus

1. 위치 없이 생성·수정(기존 클라) → 위치 없음으로 저장, 응답에 null 3필드 → Task 1 `위치_없이_만들_수_있다`, Task 2 동일.
2. 좌표만 있고 장소명 없음(또는 반대)인 모순 입력 → 400 거부 → Task 1 `좌표와_장소명은_함께여야_한다`, Task 2 동일.
3. 범위 밖 좌표(lat 100, lng 999) → 400 거부 → Task 1 `범위_밖_좌표는_거부한다`, Task 2 동일.
4. 수정 때 위치를 null 로 보내 **지우기** 가능해야 → Task 1 `수정으로_위치를_지울_수_있다`, Task 2 동일.
5. 웹 geolocation 권한 거부/실패 시 "지도에서 찍기"로 폴백, 화면 안 깨짐 → Task 3 수동 확인 단계.

---

### Task 1: 게시글 위치 첨부 (server)

**Files:**
- Modify: `api/src/main/java/com/ourtoday/api/post/Post.java` (컬럼 3개 + create/update 시그니처), `dto/PostCreateRequest.java`, `dto/PostUpdateRequest.java`, `dto/PostResponse.java`, `PostService.java` (create/update/toResponse 매핑)
- Create: `api/src/main/java/com/ourtoday/api/post/dto/PlaceLocation.java` (공용 중첩 record)
- Test: `api/src/test/java/com/ourtoday/api/post/PostLocationFlowTest.java`
- Modify: `API.md` (Post 요청/응답에 location 필드), `schema.dbml` (posts 컬럼), `api/http/api.http` (위치 포함 예시)

**Interfaces:**
- Consumes: 기존 `coupleService.getConnectedCoupleId`, `getPostInMyCouple`, `getMyPost` 가드.
- Produces:
  - `record PlaceLocation(@DecimalMin("-90") @DecimalMax("90") double lat, @DecimalMin("-180") @DecimalMax("180") double lng, @NotBlank @Size(max=100) String placeName)` — `com.ourtoday.api.post.dto` (Task 5 캘린더는 자체 복제본 사용; 공유 안 함, 도메인별 dto 패키지 유지).
  - `Post.create(coupleId, authorId, content, postedDate, Double placeLat, Double placeLng, String placeName)` / `Post.updateLocation(...)` 또는 `update(...)` 확장.
  - `PostResponse` 에 `@Schema PlaceLocation location`(없으면 null) 필드 추가.

- [ ] **Step 1: 실패 테스트 작성** — `PostLocationFlowTest` 에 4개:
  - `위치와_함께_게시글을_만들면_응답에_location_이_담긴다`: POST `/posts` body 에 `location:{lat:37.5447,lng:127.0558,placeName:"성수동"}` → 201, `location.placeName == "성수동"`.
  - `위치_없이_만들_수_있다`: location 생략 → 201, `location == null`.
  - `좌표와_장소명은_함께여야_한다`: `lat`만 있고 `placeName` 누락된 location → 400 `VALIDATION_FAILED`. (record 를 nullable 필드로 두되, location 객체가 오면 세 값 모두 필수: `@Valid` + 전부 `@NotNull`/`@NotBlank`.)
  - `범위_밖_좌표는_거부한다`: `lat:100` → 400.
  - `수정으로_위치를_지울_수_있다`: 위치 있는 글을 PUT location=null → 200, `location == null`.
- [ ] **Step 2: 실패 확인** — Run: `cd api && ./gradlew test --tests PostLocationFlowTest` · Expected: FAIL(컴파일 에러 또는 필드 없음).
- [ ] **Step 3: 구현** — `PlaceLocation` record 생성(검증 어노테이션 포함). `Post` 에 `place_lat`(Double), `place_lng`(Double), `place_name`(String, length 100) 컬럼 + create/update 에 세 인자 추가(null 허용). `PostCreateRequest`/`PostUpdateRequest` 에 `@Valid PlaceLocation location`(nullable) 추가. `PostService.create/update` 에서 `request.location()` 을 세 컬럼으로 풀어 저장, `toResponse` 에서 셋이 non-null 이면 `PlaceLocation` 조립. 세 값 부분 누락은 record 필드 검증이 막음.
- [ ] **Step 4: 통과 확인** — Run: `cd api && ./gradlew test --tests PostLocationFlowTest` · Expected: PASS.
- [ ] **Step 5: 문서 반영** — `API.md`·`schema.dbml`·`api/http/api.http` 에 위치 필드 추가.
- [ ] **Step 6: 커밋** — `git add -A && git commit -m "feat(api): 게시글에 위치(좌표+장소명) 선택 첨부"`

---

### Task 2: 캘린더 메모 위치 첨부 (server)

**Files:**
- Modify: `api/src/main/java/com/ourtoday/api/calendar/CalendarMemo.java`, `dto/CalendarMemoCreateRequest.java`, `dto/CalendarMemoUpdateRequest.java`, `dto/CalendarMemoResponse.java`, `CalendarMemoService.java`
- Create: `api/src/main/java/com/ourtoday/api/calendar/dto/PlaceLocation.java` (캘린더 도메인용 복제 record)
- Test: `api/src/test/java/com/ourtoday/api/calendar/CalendarMemoLocationFlowTest.java`
- Modify: `API.md`, `schema.dbml`, `api/http/api.http`

**Interfaces:**
- Consumes: 기존 `getMemoInMyCouple`, `getMyMemo` 가드, `blankToNull` 헬퍼 패턴.
- Produces: `CalendarMemoResponse` 에 `PlaceLocation location`(없으면 null). `CalendarMemo.create/update` 에 위치 3인자 추가.

- [ ] **Step 1: 실패 테스트 작성** — `CalendarMemoLocationFlowTest` 에 Task 1 과 같은 4개 시나리오(생성·위치없음·좌표/장소명 함께·범위밖·수정으로 지움)를 캘린더 엔드포인트(`POST /calendar-memos`, `PUT /calendar-memos/{id}`)로.
- [ ] **Step 2: 실패 확인** — Run: `cd api && ./gradlew test --tests CalendarMemoLocationFlowTest` · Expected: FAIL.
- [ ] **Step 3: 구현** — Task 1 과 동일한 방식으로 `CalendarMemo` 에 컬럼 3개 + create/update 확장, DTO 에 `@Valid PlaceLocation location`, Service 매핑. (캘린더 `update` 는 "전체 교체"라 location=null 이면 지움 — 기존 description/color 와 동일 의미.)
- [ ] **Step 4: 통과 확인** — Run: `cd api && ./gradlew test --tests CalendarMemoLocationFlowTest` · Expected: PASS.
- [ ] **Step 5: 문서 반영** — `API.md`·`schema.dbml`·`api/http/api.http`.
- [ ] **Step 6: 커밋** — `git add -A && git commit -m "feat(api): 캘린더 메모에 위치 선택 첨부"`

---

### Task 3: 카카오맵 로더 + 위치 선택 컴포넌트 (web)

**Files:**
- Create: `web/src/lib/kakaoMap.ts` (SDK 로더 — `kakao.ts` 의 SDK 로더 패턴 참고), `web/src/components/LocationPicker.tsx`
- Modify: `web/src/components/Icons.tsx` (핀 아이콘 SVG 추가), `web/next.config.ts` 또는 `.env`(카카오맵 JS 키 노출 — 빌더가 기존 카카오 키 설정 위치에 맞춤)
- 역지오코딩: 카카오 로컬 REST 는 서버 프록시가 필요할 수 있음(REST 키 노출 방지) → `web/src/app/api/.../route.ts` 또는 백엔드 경유. **빌더 판단**: JS SDK 의 `services.Geocoder.coord2Address` 로 클라이언트에서 해결되면 서버 불필요(권장).

**Interfaces:**
- Produces: `LocationPicker` 컴포넌트.
  - props: `value: { lat: number; lng: number; placeName: string } | null`, `onChange: (v: {lat,lng,placeName} | null) => void`.
  - 동작: ①"현재 위치" 버튼 → `navigator.geolocation.getCurrentPosition` → 좌표 → `coord2Address` 로 장소명 → onChange. ②지도 탭/드래그로 핀 이동 → 같은 역지오코딩. ③"지우기" → onChange(null).
  - geolocation 실패/거부 시 지도 중심(기본: 서울시청 또는 마지막 핀)에서 직접 찍기로 폴백, 에러 던지지 않음.

- [ ] **Step 1: 로더 작성** — `kakaoMap.ts`: `loadKakaoMap(): Promise<KakaoMaps>` — 스크립트 1회 주입(`//dapi.kakao.com/v2/maps/sdk.js?appkey=...&libraries=services&autoload=false`), `kakao.maps.load` 후 resolve. `kakao.ts` 로더 에러 처리 방식 재사용.
- [ ] **Step 2: 컴포넌트 작성** — `LocationPicker.tsx`: 위 props·동작 구현. 핀 아이콘은 `Icons.tsx` SVG. geolocation 거부 시 안내 문구 + 지도 찍기 유지.
- [ ] **Step 3: 린트** — Run: `cd web && npm run lint` · Expected: 통과(에러 0).
- [ ] **Step 4: 수동 확인(Review Focus 5)** — 개발 서버에서 LocationPicker 를 임시 페이지나 스토리로 띄워: 현재 위치 버튼 동작, **권한 거부 시 지도 찍기로 폴백·화면 안 깨짐**, 핀 이동 시 장소명 갱신 확인.
- [ ] **Step 5: 커밋** — `git add -A && git commit -m "feat(web): 카카오맵 위치 선택 컴포넌트"`

---

### Task 4: 게시글 작성·상세에 위치 통합 (web)

**Files:**
- Modify: `web/src/components/PostForm.tsx` (PostFormValue 에 location 추가 + LocationPicker 끼움), `web/src/app/(main)/posts/new/page.tsx`, `web/src/app/(main)/posts/[id]/edit/page.tsx` (초기 location 전달), `web/src/app/(main)/posts/[id]/page.tsx` (상세에 핀/장소명 표시), `web/src/components/PostCard.tsx`(피드 카드에 장소명 한 줄 — 선택), 게시글 타입 정의 파일(`location` 필드 추가)

**Interfaces:**
- Consumes: Task 1 의 `PostResponse.location`, Task 3 의 `LocationPicker`.
- Produces: `PostFormValue` 에 `location: {lat,lng,placeName} | null`. 저장 시 `api("/posts", {body:{...value}})` 로 그대로 전송(서버 DTO 와 필드명 일치).

- [ ] **Step 1: 폼 통합** — `PostForm` 에 LocationPicker 추가, `PostFormValue.location` 추가, submit body 에 포함. `posts/new`·`edit` 에서 기존 location 을 초기값으로.
- [ ] **Step 2: 표시** — 상세 페이지에 위치 있으면 장소명 + 작은 지도(또는 핀 + 탭 시 지도). `PostCard` 는 장소명 한 줄(이모티콘 금지, 핀 SVG).
- [ ] **Step 3: 린트** — Run: `cd web && npm run lint` · Expected: 통과.
- [ ] **Step 4: 수동 확인** — 글 작성 시 위치 추가·저장, 상세에서 표시, 수정에서 유지·변경·삭제, 위치 없이 작성한 글도 정상.
- [ ] **Step 5: 커밋** — `git add -A && git commit -m "feat(web): 게시글 작성·상세에 위치 표시"`

---

### Task 5: 캘린더 작성·표시에 위치 통합 (web)

**Files:**
- Modify: `web/src/app/(main)/calendar/page.tsx` (메모 작성/수정 UI 에 LocationPicker, 메모 표시에 장소명/핀), 캘린더 메모 타입 정의(`location` 필드 추가)

**Interfaces:**
- Consumes: Task 2 의 `CalendarMemoResponse.location`, Task 3 의 `LocationPicker`.

- [ ] **Step 1: 통합** — 캘린더 메모 작성/수정 폼에 LocationPicker, 저장 body 에 location 포함. 메모 상세/목록에 장소명 표시(핀 SVG).
- [ ] **Step 2: 린트** — Run: `cd web && npm run lint` · Expected: 통과.
- [ ] **Step 3: 수동 확인** — 메모에 위치 추가·표시·수정·삭제, 위치 없는 메모 정상.
- [ ] **Step 4: 커밋** — `git add -A && git commit -m "feat(web): 캘린더 메모에 위치 표시"`

---

## 사용자가 직접 할 일

- 카카오 개발자 콘솔에서 **지도 JS 키 + 로컬 REST** 활성화(기존 카카오 앱 키 재사용 가능 여부 확인), 웹 도메인 등록.
- 카카오맵 JS 키를 웹 환경변수로 주입(빌더가 위치 안내 → 사용자가 값 입력).
- 개인정보처리방침에 "기록에 첨부한 위치(이용자가 직접 입력/선택)" 추가.
