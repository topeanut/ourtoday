# 오늘의 우리 — API 명세

> 기능/도메인 설명은 [PLAN.md](./PLAN.md) 참조. 이 문서는 엔드포인트만.

---

## 공통

### Base URL
- 로컬: `http://localhost:8080/api/v1`
- 운영: `https://api.ourtoday.app/api/v1` (도메인 미정)

### 인증
- 인증 필요한 모든 엔드포인트는 `Authorization: Bearer {accessToken}` 헤더 필수
- Access Token: JWT, 만료 30분
- Refresh Token: JWT, 만료 14일. HttpOnly Secure 쿠키로 발급

### 응답 형식
모든 응답은 JSON. 성공 시 도메인 객체 그대로, 실패 시 표준 에러 객체.

```json
// 성공
{ "id": "...", "content": "..." }

// 실패
{
  "error": {
    "code": "COUPLE_REQUIRED",
    "message": "커플 연결이 필요한 기능입니다.",
    "timestamp": "2026-05-25T10:23:45Z"
  }
}
```

### 표준 에러 코드

| 코드 | HTTP | 의미 |
|------|------|------|
| `UNAUTHORIZED` | 401 | 토큰 없음/만료 |
| `FORBIDDEN` | 403 | 권한 없음 (다른 커플의 데이터 접근 등) |
| `COUPLE_REQUIRED` | 403 | 커플 매칭 전, 해당 기능 사용 불가 |
| `NOT_FOUND` | 404 | 리소스 없음 |
| `VALIDATION_FAILED` | 400 | 요청 검증 실패 (필드별 상세는 `details` 필드) |
| `INVITE_CODE_INVALID` | 400 | 초대코드 없음/만료 |
| `INVITE_CODE_EXPIRED` | 400 | 초대코드 만료 |
| `ALREADY_IN_COUPLE` | 409 | 이미 활성 커플 있음 |
| `INTERNAL_ERROR` | 500 | 서버 에러 |

### 인증 가드 (커플 매칭 전 차단)
다음 그룹의 엔드포인트는 **커플 매칭 후에만 사용 가능**. 매칭 전 호출 시 `COUPLE_REQUIRED` 반환:
- 게시글, 반응, 캘린더 메모, 버킷리스트, 통계, 이미지 업로드

매칭 전에도 허용되는 엔드포인트:
- 인증 전체, 온보딩 상태, `/couples/invite`, `/couples/join`, `/users/me`(읽기/수정), `/users/me/settings`

---

## 1. 인증

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/auth/kakao/login` | ❌ | 카카오 로그인 페이지로 리다이렉트 |
| GET | `/auth/kakao/callback` | ❌ | 카카오 콜백 — 코드 받아 JWT 발급 |
| POST | `/auth/refresh` | ❌ (쿠키) | Refresh 쿠키로 Access Token 재발급 |
| POST | `/auth/logout` | ✅ | Refresh Token 무효화 |

**응답 (`/auth/kakao/callback`)**
```json
{
  "accessToken": "eyJ...",
  "user": {
    "id": "uuid",
    "nickname": "민지",
    "profileImageUrl": "https://...",
    "hasCouple": false
  }
}
```

---

## 2. 온보딩

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/users/me/onboarding-status` | ✅ | 매칭 상태 체크 |

**응답**
```json
{ "status": "NO_COUPLE" }       // 커플 없음 → 초대코드 화면
{ "status": "PENDING_INVITE" }  // 내가 초대코드 만들고 대기 중
{ "status": "CONNECTED" }       // 매칭 완료 → 홈으로
```

---

## 3. 커플

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| POST | `/couples/invite` | ✅ | 초대코드 생성 (커플 없는 유저만) |
| POST | `/couples/join` | ✅ | `{ "inviteCode": "ABC123" }` 로 합류 |
| GET | `/couples/me` | ✅ (커플 ok) | 내 커플 정보 |
| PATCH | `/couples/me` | ✅ (커플 ok) | `{ "startedAt": "2024-03-14" }` 등 수정 |
| DELETE | `/couples/me` | ✅ (커플 ok) | 커플 해제 |

**응답 (`POST /couples/invite`)**
```json
{
  "inviteCode": "ABC123",
  "expiresAt": "2026-05-26T10:00:00Z"
}
```

**응답 (`GET /couples/me`)**
```json
{
  "id": "uuid",
  "startedAt": "2024-03-14",
  "status": "CONNECTED",
  "members": [
    { "id": "uuid", "nickname": "민지", "profileImageUrl": "..." },
    { "id": "uuid", "nickname": "지호", "profileImageUrl": "..." }
  ]
}
```

---

## 4. 게시글 (3줄 일기)

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| POST | `/posts` | ✅ (커플) | 작성 |
| GET | `/posts` | ✅ (커플) | 우리 커플의 게시글 목록 (페이징) |
| GET | `/posts/{id}` | ✅ (커플) | 상세 (공유 링크에서 호출) |
| PATCH | `/posts/{id}` | ✅ (커플) | 수정 (작성자만) |
| DELETE | `/posts/{id}` | ✅ (커플) | 삭제 (작성자만) |
| GET | `/posts/memories/today` | ✅ (커플) | 작년 오늘 (여러 해 묶어서) |

**요청 (`POST /posts`)**
```json
{
  "content": "오늘 처음 같이 본 영화",
  "postedDate": "2026-05-25",
  "imageUrls": ["https://s3.../1.jpg", "https://s3.../2.jpg"]
}
```

**쿼리 파라미터 (`GET /posts`)**
- `page` (default 0), `size` (default 20)
- `author` — `me` / `partner` (선택)
- `from`, `to` — YYYY-MM-DD (선택, posted_date 범위)

**응답 (`GET /posts/{id}`)**
```json
{
  "id": "uuid",
  "author": { "id": "uuid", "nickname": "민지" },
  "content": "...",
  "postedDate": "2026-05-25",
  "images": [{ "url": "...", "order": 0 }],
  "reactions": [
    { "emojiType": "HEART", "count": 2, "reactedByMe": true },
    { "emojiType": "LAUGH", "count": 1, "reactedByMe": false }
  ],
  "createdAt": "...",
  "updatedAt": "..."
}
```

**응답 (`GET /posts/memories/today`)**
```json
{
  "today": "2026-05-25",
  "memories": [
    { "year": 2025, "posts": [/* posts */] },
    { "year": 2024, "posts": [/* posts */] }
  ]
}
```

---

## 5. 이미지 업로드 (Presigned URL)

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| POST | `/images/presigned-url` | ✅ (커플) | S3 업로드용 URL 발급 |

**요청**
```json
{ "filename": "photo.jpg", "contentType": "image/jpeg" }
```

**응답**
```json
{
  "uploadUrl": "https://s3.amazonaws.com/...?signature=...",
  "publicUrl": "https://cdn.ourtoday.app/posts/uuid.jpg",
  "expiresInSec": 600
}
```

> 클라이언트가 `uploadUrl` 로 S3 직업로드 → 응답의 `publicUrl` 을 `POST /posts` 요청에 포함

---

## 6. 반응 (이모지)

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| POST | `/posts/{id}/reactions` | ✅ (커플) | `{ "emojiType": "HEART" }` 토글 |

**동작**: 동일 emojiType이 있으면 제거, 없으면 추가. 같은 유저가 다른 이모지는 여러 개 가능.

**응답**
```json
{ "reacted": true, "emojiType": "HEART" }   // 추가됨
{ "reacted": false, "emojiType": "HEART" }  // 제거됨
```

---

## 7. 캘린더 메모 (일정)

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| POST | `/calendar/memos` | ✅ (커플) | 메모 생성 |
| GET | `/calendar/memos` | ✅ (커플) | 월간/기간 조회. `?from=YYYY-MM-DD&to=YYYY-MM-DD` |
| GET | `/calendar/memos/{id}` | ✅ (커플) | 상세 |
| PATCH | `/calendar/memos/{id}` | ✅ (커플) | 수정 (작성자만) |
| DELETE | `/calendar/memos/{id}` | ✅ (커플) | 삭제 (작성자만) |

**요청 (`POST /calendar/memos`)**
```json
{
  "date": "2026-06-14",
  "title": "1주년 기념일",
  "description": "OO 호텔 예약",
  "color": "#FFB6C1"
}
```

**응답 (`GET /calendar/memos?from=2026-06-01&to=2026-06-30`)**
```json
{
  "items": [
    {
      "id": "uuid",
      "date": "2026-06-14",
      "title": "1주년 기념일",
      "description": "OO 호텔 예약",
      "color": "#FFB6C1",
      "author": { "id": "uuid", "nickname": "민지" }
    }
  ]
}
```

---

## 8. 버킷리스트

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| POST | `/bucket-items` | ✅ (커플) | 추가 |
| GET | `/bucket-items` | ✅ (커플) | 목록. `?status=ALL\|TODO\|DONE` |
| PATCH | `/bucket-items/{id}` | ✅ (커플) | 수정 (작성자만) |
| PATCH | `/bucket-items/{id}/complete` | ✅ (커플) | `{ "postId": "uuid?" }` 완료 처리 |
| DELETE | `/bucket-items/{id}` | ✅ (커플) | 삭제 (작성자만) |

**요청 (`POST /bucket-items`)**
```json
{
  "title": "오로라 보러 가기",
  "description": "아이슬란드",
  "targetDate": "2027-01-01"
}
```

---

## 9. 통계

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/couples/me/stats` | ✅ (커플) | 두 사람의 작성 통계 한 번에 |

**응답**
```json
{
  "me": {
    "totalPosts": 200,
    "thisMonthPosts": 12,
    "consecutiveDays": 5,
    "totalMemos": 30,
    "totalReactionsGiven": 450
  },
  "partner": {
    "totalPosts": 180,
    "thisMonthPosts": 10,
    "consecutiveDays": 3,
    "totalMemos": 22,
    "totalReactionsGiven": 410
  },
  "couple": {
    "daysTogether": 437,
    "firstPostDate": "2024-03-15"
  }
}
```

> "이번 달" 기준: 서버 타임존 기준 현재 월의 1일~말일
> "연속 작성일": 오늘부터 거꾸로, 게시글이 끊긴 날까지의 연속 일수

---

## 10. 마이페이지

### 10-1. 프로필
| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/users/me` | ✅ | 내 프로필 |
| PATCH | `/users/me` | ✅ | 닉네임/프로필 사진 수정 |

**요청 (`PATCH /users/me`)**
```json
{ "nickname": "민지", "profileImageUrl": "https://..." }
```

**응답 (`GET /users/me`)**
```json
{
  "id": "uuid",
  "nickname": "민지",
  "profileImageUrl": "https://...",
  "kakaoId": "1234567890",
  "createdAt": "2024-03-14T10:00:00Z",
  "hasCouple": true
}
```

### 10-2. 알림 설정
| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/users/me/settings` | ✅ | 알림 설정 조회 |
| PATCH | `/users/me/settings` | ✅ | 알림 설정 수정 |

**요청/응답**
```json
{
  "notifyMemoryToday": true,
  "notifyPartnerPost": true
}
```

### 10-3. 데이터 내보내기
| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| POST | `/users/me/export` | ✅ | 본인 데이터 내보내기 요청 (zip 다운로드 URL 반환) |

**응답**
```json
{
  "downloadUrl": "https://cdn.ourtoday.app/exports/uuid.zip",
  "expiresAt": "2026-05-26T10:00:00Z"
}
```

> MVP에선 동기 처리로 시작 가능. 이미지가 많으면 비동기(이메일로 링크 발송)로 전환 검토.

---

## 부록: HTTP 메서드 사용 컨벤션

| 메서드 | 용도 |
|--------|------|
| `GET` | 조회. 부수 효과 없음 |
| `POST` | 생성. 또는 멱등하지 않은 액션 (로그아웃, export 요청) |
| `PATCH` | 부분 수정 |
| `PUT` | 전체 교체 (이 프로젝트에선 거의 안 씀) |
| `DELETE` | 삭제 |

## 부록: 페이징 컨벤션

목록 조회는 모두 `?page=0&size=20` 형식. 응답에 `Spring Data Page` 형식 그대로:

```json
{
  "content": [/* items */],
  "totalElements": 123,
  "totalPages": 7,
  "number": 0,
  "size": 20
}
```
