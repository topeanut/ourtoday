# 오늘의 우리 — 프로젝트 플랜

> **한 줄 정의**: 연인들이 부담 없이 관계를 기록하고, 시간이 지나 추억을 다시 발견하게 만드는 초경량 아카이빙 앱

> **API 명세는 [API.md](./API.md) 참조.** 이 문서는 기능/모델/로드맵 중심.

---

## 1. 프로젝트 개요

### 핵심 가치
- 길게 쓰지 않아도 됨 (3줄 + 이미지)
- 매일 부담 없음 (**작성은 선택사항**)
- 시간이 지날수록 가치가 커짐 (작년 오늘, 버킷리스트 회고, 통계)

### MVP 기능 범위
- [x] 카카오 소셜 로그인
- [x] 초대코드 기반 커플 연결
- [x] 3줄 일기 + 이미지 게시판
- [x] 반응 이모지 (댓글/대댓글 X)
- [x] 게시글 공유 (ID 기반 URL, UI는 아이콘)
- [x] 작년 오늘 회고
- [x] 데이트 버킷리스트
- [x] **캘린더 메모** (일정/한 줄 메모, 커플 공유)
- [x] **마이페이지** (프로필/커플 정보/알림 설정/해제/내보내기)
- [x] **활동 통계** (상대·내 작성 횟수: 누적/이번 달/연속일)

### 의도적으로 배제
- 댓글/대댓글 — 부담을 늘림
- 좋아요 카운트 노출 경쟁 — 반응 이모지로 대체
- 친구 추가, 팔로우 같은 SNS 요소

---

## 2. 기술 스택

| 영역 | 선택 | 비고 |
|------|------|------|
| 프론트 | Next.js (App Router) | 별도 인원이 있다고 가정, 이 플랜은 백엔드 중심 |
| 백엔드 | Spring Boot 3.x + Java 21 | LTS, record/패턴매칭 등 최신 문법 활용 |
| ORM | Spring Data JPA (Hibernate) | 초보 학습 곡선 ↓ |
| DB | PostgreSQL 16 | 로컬: Docker, 운영: RDS or Supabase |
| 인증 | Spring Security + OAuth2 Client (Kakao) | JWT로 세션 관리 |
| 이미지 저장 | AWS S3 + CloudFront | Presigned URL 업로드 방식 |
| 빌드 | Gradle (Kotlin DSL) | |
| API 문서 | springdoc-openapi (Swagger UI) | 프론트와 협업할 때 필수 |
| 배포 | Docker + AWS (EC2 or ECS Fargate) | 초보면 EC2 + docker-compose부터 |
| CI/CD | GitHub Actions | |

> **왜 이 조합인가**: Spring Security OAuth2 Client는 카카오 같은 OAuth 제공자와의 토큰 교환을 거의 자동화해줍니다. NextAuth와 이중으로 처리하면 흐름이 꼬이니, **인증은 백엔드(Spring)에서 책임지고 프론트는 JWT만 받아서 쓰는 구조**가 깔끔합니다.

---

## 3. UI 구조 (프론트 가이드)

> 백엔드 PLAN 문서지만, API 그룹 정리에 영향을 주므로 같이 적어둡니다.

### 메인 네비게이션 — 4탭
1. **홈** — 우리 커플의 게시글 피드 (작년 오늘 카드 포함)
2. **버킷** — 데이트 버킷리스트
3. **캘린더** — 월간 캘린더 + 메모/일정 표시
4. **마이페이지** — 프로필, 커플 정보, 알림 설정, 통계, 해제, 내보내기

### 디바이스별 네비게이션
- **모바일**: 하단 탭바
- **PC**: **좌측 네비게이션** (사이드바)

### 공유 UX
- 게시글 우상단의 **공유 아이콘** 클릭 → 시스템 공유 시트(URL 복사 등)
- 백엔드는 공유 URL을 위한 별도 작업 거의 없음 — `GET /posts/{id}` 가 공유 링크의 본문

---

## 4. 온보딩 정책

**옵션 A 채택**: 카카오 로그인 + 커플 연결 전엔 **앱의 거의 모든 기능이 잠긴 상태**.

### 흐름
```
[1] 카카오 로그인 → User 레코드 생성
[2] 커플 상태 확인 (GET /users/me/onboarding-status)
    ├─ 커플 없음 → 초대코드 화면만 노출
    │   ├─ "초대코드 만들기" → POST /couples/invite
    │   └─ "초대코드 입력하기" → POST /couples/join
    └─ 커플 매칭 완료 → 홈으로 진입
```

### 백엔드 정책
- 커플 없는 유저가 게시글/메모/버킷 등 API를 호출하면 **403 Forbidden + 표준 에러코드 `COUPLE_REQUIRED`** 반환
- 단, **허용되는 API**: 인증·온보딩·내 프로필 조회·로그아웃
- 자세한 에러 코드/응답 형식은 `API.md` 참조

---

## 5. 데이터 모델 (ERD)

ERD 시각화는 [dbdiagram.io](https://dbdiagram.io) 또는 ERDCloud 추천.

### 엔티티 목록

**User (사용자)**
- `id` (PK, UUID)
- `kakao_id` (UNIQUE) — 카카오 OAuth 식별자
- `nickname`
- `profile_image_url`
- `created_at`

**UserSettings (유저 설정)** *(신규)*
- `user_id` (PK, FK → User)
- `notify_memory_today` (boolean, default true) — "작년 오늘" 알림
- `notify_partner_post` (boolean, default true) — 상대가 글/메모 작성 시 알림
- `updated_at`

**Couple (커플)**
- `id` (PK, UUID)
- `invite_code` (UNIQUE, 6~8자리 영숫자) — 만료 시간 필요. 매칭 완료 시 NULL
- `invite_code_expires_at`
- `started_at` (DATE) — 사귄 날짜. 두 번째 멤버 합류 시점에 자동 세팅. 마이페이지에서 수동 수정 가능
- `status` (PENDING, CONNECTED, BROKEN)
- `created_at`

**CoupleMember (커플 멤버)**
- `couple_id` (FK, PK)
- `user_id` (FK, PK)
- `joined_at`

> 유저는 동시에 활성 커플 1개만 — 애플리케이션 레벨 제약

**Post (게시글 = 3줄 일기)**
- `id` (PK, UUID) — 공유 URL에 그대로 사용
- `couple_id` (FK)
- `author_id` (FK, User)
- `content` (TEXT, 길이 제한 e.g. 300자)
- `posted_date` (DATE) — "작년 오늘" 조회용 인덱스
- `created_at`, `updated_at`

**PostImage (게시글 이미지)**
- `id` (PK)
- `post_id` (FK)
- `image_url` — S3 객체 URL (or 키)
- `display_order`

**Reaction (반응 이모지)**
- `id` (PK)
- `post_id` (FK)
- `user_id` (FK)
- `emoji_type` (ENUM: HEART, LAUGH, SURPRISE, SAD, FIRE 등)
- `created_at`
- UNIQUE(`post_id`, `user_id`, `emoji_type`)

**CalendarMemo (캘린더 메모/일정)** *(신규)*
- `id` (PK, UUID)
- `couple_id` (FK)
- `author_id` (FK, User)
- `date` (DATE) — 메모가 표시될 날짜
- `title` (varchar, 50자) — 짧은 제목
- `description` (TEXT, NULL 허용) — 추가 설명
- `color` (varchar, NULL) — 캘린더 표시 색상 (선택, 예: `#FFB6C1`)
- `created_at`, `updated_at`

**BucketItem (데이트 버킷리스트)**
- `id` (PK)
- `couple_id` (FK)
- `created_by` (FK, User)
- `title`
- `description`
- `target_date` (NULL 허용)
- `is_completed`
- `completed_post_id` (FK, Post, NULL) — 완료 시 어떤 게시글에서 달성했는지 연결
- `created_at`, `completed_at`

### 주요 인덱스
- `posts(couple_id, posted_date)` — 작년 오늘 조회의 핵심
- `posts(couple_id, author_id, created_at)` — 작성자별 통계 집계
- `reactions(post_id)` — 게시글 상세에서 반응 집계
- `couples(invite_code)` — 초대코드 검색
- `calendar_memos(couple_id, date)` — 캘린더 월간 조회

---

## 6. API 명세

> **전체 명세는 [API.md](./API.md)에 정리.** 여기엔 그룹만 나열.

| 그룹 | Prefix | 비고 |
|------|--------|------|
| 인증 | `/auth/*` | 카카오 OAuth + JWT |
| 온보딩 | `/users/me/onboarding-status` | 커플 매칭 여부 체크 |
| 커플 | `/couples/*` | 초대코드, 매칭, 해제 |
| 게시글 | `/posts/*` | CRUD + 작년 오늘 |
| 이미지 | `/images/presigned-url` | S3 업로드용 |
| 반응 | `/posts/{id}/reactions` | 이모지 토글 |
| 캘린더 메모 | `/calendar/memos/*` | CRUD + 월간 조회 |
| 버킷리스트 | `/bucket-items/*` | CRUD + 완료 처리 |
| 통계 | `/couples/me/stats` | 누적/월별/연속일 |
| 마이페이지 | `/users/me/*` | 프로필, 설정, 내보내기 |

---

## 7. 단계별 개발 로드맵

> 각 Phase는 **혼자 백엔드 개발 + 학습 병행** 기준으로 산정. 시간은 가이드일 뿐.

### Phase 0 — 환경 셋업 & 기초 학습 ✅
- [x] IntelliJ IDEA 설치, JDK 21 설정
- [x] Spring Initializr로 프로젝트 생성
- [x] Docker로 PostgreSQL 띄우기 (`docker-compose.yml`)
- [x] Spring Boot 첫 부팅 성공
- [ ] Hello World REST API 동작 확인
- [ ] **학습**: Spring Boot 입문, JPA 기본 (Entity, Repository), DTO/Service 계층 패턴

### Phase 1 — 인증 + 커플 연결 (2~3주)
- [ ] User, UserSettings 엔티티/Repository/Service
- [ ] 카카오 개발자센터 앱 등록, OAuth Redirect URI 설정
- [ ] Spring Security OAuth2 Client로 카카오 로그인 흐름 구현
- [ ] JWT 발급/검증 필터 작성
- [ ] Couple/CoupleMember 엔티티 + 초대코드 생성/조인 API
- [ ] 온보딩 상태 체크 API + `COUPLE_REQUIRED` 가드 필터/AOP
- [ ] **학습**: Spring Security 필터 체인, OAuth2 흐름, JWT

> **이 구간이 백엔드 초보에게 가장 큰 벽**입니다. 충분히 시간 쓰세요.

### Phase 2 — 게시글 CRUD (1~2주)
- [ ] Post 엔티티 + 텍스트만 먼저
- [ ] 게시글 작성/조회/수정/삭제 API
- [ ] 권한 체크 (같은 커플만 조회, 작성자만 수정/삭제)
- [ ] 페이징 (Spring Data `Pageable`)

### Phase 3 — 이미지 업로드 (1~2주)
- [ ] AWS 계정 생성, S3 버킷 생성, IAM 키 발급
- [ ] AWS SDK for Java 의존성 추가
- [ ] Presigned URL 발급 API
- [ ] PostImage 엔티티 + 게시글 작성 시 이미지 URL 함께 저장
- [ ] **학습**: S3, IAM 정책, CORS 설정

### Phase 4 — 반응 이모지 + 공유 (1주)
- [ ] Reaction 엔티티 + 토글 API
- [ ] 게시글 상세 응답에 반응 집계 포함
- [ ] 공유 URL은 사실상 `GET /posts/{id}` 그대로 (프론트가 공유 시트 처리)

### Phase 5 — 캘린더 메모 + 작년 오늘 + 버킷리스트 + 통계 (2~3주)
- [ ] CalendarMemo 엔티티 + CRUD + 월간 조회 API
- [ ] `/posts/memories/today` — `posted_date`의 월/일이 오늘과 같은 게시글 묶음
- [ ] BucketItem CRUD + 완료 시 Post와 연결
- [ ] `/couples/me/stats` — 누적/이번 달/연속 작성일 집계 쿼리

### Phase 6 — 마이페이지 (1주) *(신규)*
- [ ] `/users/me` 프로필 조회/수정
- [ ] `/users/me/settings` 알림 설정 조회/수정
- [ ] `/couples/me` 커플 정보 수정 (사귄 날짜 등)
- [ ] `/couples/me` 커플 해제
- [ ] `/users/me/export` — 본인 데이터 zip으로 받기 (비동기로 충분, 일단 동기 ok)

### Phase 7 — 배포 (1~2주)
- [ ] Dockerfile 작성
- [ ] AWS EC2 인스턴스 생성, RDS PostgreSQL 생성
- [ ] 도메인 + HTTPS (Certbot + Nginx 리버스 프록시)
- [ ] GitHub Actions로 배포 자동화
- [ ] **학습**: Linux 기초, Nginx, Docker

---

## 8. 학습 로드맵 (백엔드 처음인 사람 기준)

각 Phase에 들어가기 전 필요한 학습 주제:

1. **Java 21 문법** (이미 가능하면 스킵) — Stream API, Optional, record
2. **Spring Boot 입문** — 인프런 김영한 "스프링 입문" 강의가 표준
3. **Spring Data JPA** — 김영한 "JPA 기본편" 강의
4. **Spring Security + OAuth2** — 공식 문서 + 한국어 블로그 조합
5. **REST API 설계 원칙** — Richardson Maturity Model 정도까지만
6. **Docker 기초** — `docker run`, `docker-compose` 정도면 충분
7. **AWS 기초** — EC2, RDS, S3, IAM

> 강의 다 듣고 시작하지 마세요. **필요한 만큼만 학습 → 만들기 → 막히면 다시 학습** 사이클이 가장 빠릅니다.

---

## 9. 다음 액션 아이템 (지금 당장 할 일)

순서대로 진행:

1. ✅ ~~요구사항 검토~~
2. ✅ ~~ERD 시각화~~ (DBML로 dbdiagram에 그림. 신규 엔티티 추가 필요: CalendarMemo, UserSettings)
3. ✅ ~~Spring Initializr로 프로젝트 생성~~
4. ✅ ~~Docker로 PostgreSQL 띄움 + Spring 첫 부팅~~
5. **Hello World API 만들기** — Phase 0 마지막 단계
6. **Git 저장소 생성** — GitHub에 private repo
7. **카카오 개발자 앱 등록** — Phase 1 들어가기 전 미리

---

## 10. 열어둔 결정 사항 (나중에 결정해도 됨)

- [ ] **푸시 알림**: 작년 오늘 / 상대 작성 알림은 FCM 필요. UserSettings 토글만 만들어두고 실제 발송은 나중에
- [ ] **이미지 리사이징**: 원본만 저장 vs 썸네일 미리 생성 (Lambda 트리거)
- [ ] **소프트 딜리트 vs 하드 딜리트**: 게시글/메모 삭제 정책
- [ ] **커플 해제 시 데이터 처리**: 게시글/메모는 누가 가져가는가? (현재는 둘 다 못 봄 가정)
- [ ] **데이터 내보내기 형식**: JSON 한 덩어리 vs zip(게시글 + 이미지 원본) — 후자는 무거움
- [ ] **백업 정책**: RDS 자동 백업으로 충분한가?
- [ ] **캘린더 메모 알림**: `date` 도래 전에 알림 보낼지 (D-1, 당일 등)
