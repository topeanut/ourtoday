# 게시글 댓글 — 설계

## 의도
이모지 반응만으로는 상대 기록에 마음을 전하기 부족하다. 커플 두 사람이 서로의 3줄 일기에 짧은 말을 남길 수 있게 한다.
`PLAN.md` 의 "의도적으로 배제: 댓글/대댓글" 을 **댓글만 허용, 대댓글은 계속 배제**로 바꾼다.

## 목표 / 비목표 / 반목표
- 목표: 한 단계 댓글 작성·조회·수정·삭제, 피드에서 최근 댓글 미리보기
- 비목표: 대댓글, 댓글 반응, 댓글 푸시 알림(푸시 설계에 "추후 이벤트"로만 기록), 멘션
- 반목표: SNS 처럼 보이는 것 — 이모티콘 아이콘(💬 등) 금지, 댓글 수 경쟁 강조 금지

## 접근법 (사용자 선택: B)
- A. 상세에서만 댓글, 피드엔 개수 — 가볍지만 상대 말이 피드에서 안 보임
- **B. 피드 카드에 최근 댓글 2개 미리보기 + 상세에서 전체** ← 선택. 상대가 남긴 말이 바로 보이는 게 목적에 맞음
- C. 피드에서 전부 펼침 — 카드 길이·응답 무게 문제로 제외

## 백엔드 (`com.ourtoday.api.comment`)
- 엔티티 `Comment`: `id(UUID)`, `postId`, `authorId`, `content(≤200)`, `createdAt`, `updatedAt`. 인덱스 `(post_id, created_at)`. 테이블은 `ddl-auto: update` 로 생성
- API (`@RequireCouple`, `postService.getPostInMyCouple()` 로 커플 검증)
  - `GET /api/v1/posts/{postId}/comments` — 전체, 오래된 순
  - `POST /api/v1/posts/{postId}/comments` — `{content}`
  - `PATCH /api/v1/posts/{postId}/comments/{commentId}` — 본인만
  - `DELETE /api/v1/posts/{postId}/comments/{commentId}` — 본인만
- `PostResponse` 에 `commentCount`, `recentComments`(최신 2개, 화면엔 오래된→최신 순) 추가. 피드 목록은 postIds 일괄 조회(반응 집계와 같은 방식)로 N+1 방지
- 삭제 연동: `PostService.deleteWithRelations()` 에서 `deleteByPostId`, 계정 탈퇴 시 본인 댓글 삭제
- 에러/응답 형식은 `API-CONVENTIONS.md` 따름, `API.md` 에 명세 추가
- 테스트: `ReactionFlowTest` 방식의 `CommentFlowTest` (작성·조회·수정·삭제, 다른 커플 403, 타인 댓글 수정 403, 글 삭제 시 함께 삭제, 피드 미리보기)

## 프론트 (`web/`)
- `Icons.tsx` 에 `CommentIcon`(말풍선, 기존 stroke 스타일) 추가 — 이모티콘 문자 사용 금지
- `PostCard`: 반응 줄 아래 최근 댓글 2개(닉네임 + 한 줄 말줄임), 3개 이상이면 "댓글 N개 모두 보기" → 상세로
- 상세 `posts/[id]`: 전체 댓글 목록 + 하단 입력창(200자, `KeyboardSafeScreen` 활용), 내 댓글은 더보기 메뉴로 수정/삭제
- UI 작업 시 `designing-without-ai-look` 스킬 적용

## 문서
- `PLAN.md` MVP 범위·배제 항목 갱신, `schema.dbml` 에 comments 추가

## 빌더 재량
- 입력창 세부 모양, 낙관적 업데이트 여부, 빈 상태 문구
