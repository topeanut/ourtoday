# 게시글 댓글 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 커플 두 사람이 서로의 게시글에 한 단계 댓글을 달고, 피드 카드에서 최근 댓글 2개를 미리 본다.

**Architecture:** 새 `comment` 도메인(엔티티·레포·서비스·컨트롤러)을 reaction 과 같은 구조로 추가. `PostService` 가 피드 조립 시 `CommentRepository` 로 postIds 일괄 조회해 `commentCount`·`recentComments` 를 채운다. 웹은 `PostCard` 에 미리보기, 상세 페이지에 전체 목록 + 입력창.

**Tech Stack:** Spring Boot 3 / Java 21 / JPA / H2(test) · PostgreSQL(prod), Next.js App Router + SWR + Tailwind

**Spec:** `docs/superpowers/specs/2026-11-22-post-comments-design.md`

## Global Constraints

- 댓글 최대 200자, 공백만 불가 (`@NotBlank @Size(max = Comment.CONTENT_MAX_LENGTH)`)
- 대댓글 없음 (parentId 컬럼 만들지 않음)
- 같은 커플만 조회·작성 (`@RequireCouple` + `postService.getPostInMyCouple()`), 수정·삭제는 작성자 본인만 (`FORBIDDEN`)
- 정렬: 오래된 순(`createdAt asc`). 미리보기는 최신 2개를 오래된→최신 순으로
- UI 에 이모티콘 문자(💬 등) 금지 — `Icons.tsx` SVG 스타일만
- 에러 형식 `$.error.code` (`API-CONVENTIONS.md`), 엔티티는 연관관계 없이 UUID 컬럼 (기존 패턴)
- 수정 후 린트: `cd web && npm run lint`, 백엔드는 `./gradlew build`

## Review Focus

1. 게시글 삭제 시 댓글이 남으면 안 됨 → Task 2 `게시글을_지우면_댓글도_지워진다`
2. 탈퇴한 사용자의 댓글이 상대 글에 남아 author 조회에서 NPE → Task 2 `탈퇴하면_상대_글에_단_내_댓글도_지워진다`
3. 다른 게시글 ID 경로로 남의 commentId 를 수정/삭제 (postId 불일치) → Task 1 `다른_게시글_경로로는_댓글을_수정할_수_없다` (NOT_FOUND)
4. 댓글 0개 게시글의 피드 응답이 `recentComments: []`, `commentCount: 0` 이어야 프론트가 안 깨짐 → Task 2 `댓글이_없으면_빈_목록과_0`
5. 긴 댓글이 카드 미리보기에서 레이아웃을 깨면 안 됨 → Task 4 한 줄 말줄임(`truncate`) 확인

---

### Task 1: 댓글 도메인 + CRUD API

**Files:**
- Create: `api/src/main/java/com/ourtoday/api/comment/Comment.java`, `CommentRepository.java`, `CommentService.java`, `CommentController.java`, `dto/CommentRequest.java`, `dto/CommentResponse.java`
- Test: `api/src/test/java/com/ourtoday/api/comment/CommentFlowTest.java`
- Modify: `API.md` (Comment 섹션), `PLAN.md:20,29` (MVP "댓글(대댓글 X)", 배제 항목은 "대댓글"만), `schema.dbml` (comments 테이블), `api/http/api.http` (요청 예시)

**Interfaces:**
- Produces:
  - `Comment` 엔티티: 테이블 `comments`, 인덱스 `idx_comments_post_created (post_id, created_at)`; 필드 `id, postId, authorId, content, createdAt, updatedAt`; `static Comment create(UUID postId, UUID authorId, String content)`, `void update(String content)`, `boolean isWrittenBy(UUID)`, `CONTENT_MAX_LENGTH = 200`
  - `CommentRepository`: `List<Comment> findByPostIdOrderByCreatedAtAsc(UUID)`, `List<Comment> findByPostIdInOrderByCreatedAtAsc(Collection<UUID>)`, `void deleteByPostId(UUID)`, `void deleteByAuthorId(UUID)`
  - `CommentResponse(UUID id, PostResponse.Author author, String content, Instant createdAt, Instant updatedAt)`
  - API: `GET /api/v1/posts/{postId}/comments` → `List<CommentResponse>` 200, `POST` → 201, `PATCH /{commentId}` → 200, `DELETE /{commentId}` → 204. 요청 본문 `CommentRequest(String content)`

- [ ] **Step 1: `CommentFlowTest` 작성** — `ReactionFlowTest` 의 setUp(me/partner/stranger + me 의 게시글) 복사. 테스트:
  - `상대가_댓글을_달면_오래된_순으로_조회된다`: partner "좋았어", me "또 가자" 작성 → GET `$.length()`=2, `$[0].content`="좋았어", `$[0].author.nickname`="지호", POST 응답 201
  - `다른_커플은_조회도_작성도_못한다`: stranger GET/POST → 403 `FORBIDDEN`
  - `내_댓글만_수정_삭제할_수_있다`: partner 댓글을 me 가 PATCH/DELETE → 403; partner PATCH → 200 `$.content` 변경; partner DELETE → 204 후 GET 길이 0
  - `다른_게시글_경로로는_댓글을_수정할_수_없다`: me 의 두 번째 게시글 ID 경로로 PATCH → 404 `NOT_FOUND`
  - `빈_댓글과_200자_초과는_거절된다`: `"  "` 와 `"가".repeat(201)` → 400 `VALIDATION_FAILED`; `"가".repeat(200)` → 201
- [ ] **Step 2: 실행해 실패 확인** — `cd api && ./gradlew test --tests '*CommentFlowTest'` → 컴파일 실패/404
- [ ] **Step 3: 구현** — 서비스는 모든 메서드에서 `postService.getPostInMyCouple(userId, postId)` 먼저 호출. 수정/삭제는 `commentRepository.findById` 후 `postId` 불일치면 `NOT_FOUND("댓글을 찾을 수 없습니다.")`, 작성자 아니면 `FORBIDDEN("작성자만 수정/삭제할 수 있습니다.")`. author 는 `UserRepository.findAllById` 로 일괄 조회. 컨트롤러 `@Tag(name="Comment")`, `@RequireCouple`
- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS
- [ ] **Step 5: 문서 갱신** (API.md, PLAN.md, schema.dbml, api.http)
- [ ] **Step 6: 커밋** — `feat(api): 게시글 댓글 CRUD API`

### Task 2: 피드 미리보기 + 삭제 연동

**Files:**
- Modify: `api/.../post/dto/PostResponse.java` (필드 추가), `api/.../post/PostService.java` (`loadComments`, `toResponse`, `deleteWithRelations`), `api/.../account/AccountService.java:45` 근처, `API.md` (PostResponse 필드), `docs/superpowers/specs/2026-11-22-push-notifications-design.md` (이벤트 목록에 "댓글 — 추후" 한 줄)
- Modify: `api/.../account/ExportService.java` 는 **건드리지 않음** (`PostResponse` 를 생성하는 곳이 있으면 컴파일만 맞춤)
- Test: `CommentFlowTest` 에 추가

**Interfaces:**
- Consumes: Task 1 `CommentRepository`, `CommentResponse`
- Produces: `PostResponse` 에 `long commentCount`, `List<CommentResponse> recentComments` (reactions 다음 위치). 목록·상세·작년 오늘 모두 채워짐

- [ ] **Step 1: 테스트 추가**
  - `피드에_최근_댓글_2개와_개수가_나온다`: 댓글 "1","2","3" 순서로 작성 → `GET /api/v1/posts` 의 `$.content[0].commentCount`=3, `recentComments[*].content` = `contains("2","3")`
  - `댓글이_없으면_빈_목록과_0`: 새 글 상세 → `commentCount`=0, `recentComments.length()`=0
  - `게시글을_지우면_댓글도_지워진다`: 댓글 작성 → me 가 글 DELETE → `commentRepository.count()`=0
  - `탈퇴하면_상대_글에_단_내_댓글도_지워진다`: partner 가 me 글에 댓글 → partner 탈퇴(`AccountService.withdraw` 또는 해당 API) → 레포에서 partner 댓글 0
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*CommentFlowTest'`
- [ ] **Step 3: 구현** — `loadComments(postIds)` 는 `findByPostIdInOrderByCreatedAtAsc` 로 전부 가져와 postId 별 그룹 → 개수 + 마지막 2개 (커플 2인이라 글당 댓글 수가 작아 일괄 로드로 충분, H2·Postgres 공통 쿼리 유지). 댓글 작성자 닉네임은 기존 `loadUsers` 에 합쳐서 한 번에. `deleteWithRelations` 에 `commentRepository.deleteByPostId`, `withdraw` 에 `commentRepository.deleteByAuthorId(userId)` (`postService.deleteAllWrittenBy` 뒤)
- [ ] **Step 4: 전체 테스트** — `./gradlew build` → BUILD SUCCESSFUL (기존 Post/Reaction/MyPage 테스트 포함)
- [ ] **Step 5: 커밋** — `feat(api): 피드에 최근 댓글 미리보기, 글 삭제·탈퇴 시 댓글 정리`

### Task 3: 웹 — 타입, 아이콘, 피드 카드 미리보기

**Files:**
- Modify: `web/src/lib/types.ts` (`Comment` 타입, `Post.commentCount`, `Post.recentComments`), `web/src/components/Icons.tsx` (`CommentIcon`), `web/src/components/PostCard.tsx`

**Interfaces:**
- Consumes: Task 2 응답 형태
- Produces: `export interface Comment { id: UUID; author: Post["author"]; content: string; createdAt: string; updatedAt: string }`, `export const CommentIcon = ({ className }: P) => ...` (말풍선, 기존 `svg()` 헬퍼, stroke)

- [ ] **Step 1: 구현** — `designing-without-ai-look` 스킬 먼저 로드. 반응 줄 아래 구분선 + 미리보기 행 `닉네임(굵게) 본문(truncate 한 줄)`. `commentCount > 0` 일 때만 표시. `linkToDetail` 이 true 면 하단에 `CommentIcon` + "댓글 N개 모두 보기"(N≥3) 또는 "댓글 달기"(N<3) 링크 → `/posts/{id}`. 상세(`linkToDetail=false`)에서는 미리보기 숨김(상세는 전체 목록이 대신함)
- [ ] **Step 2: 린트·빌드** — `cd web && npm run lint && npm run build` → 에러 0
- [ ] **Step 3: 브라우저 확인** — `run` 스킬로 로컬 띄워 피드 카드에서 긴 댓글이 한 줄로 잘리는지, 이모티콘 없는지 확인
- [ ] **Step 4: 커밋** — `feat(web): 피드 카드에 최근 댓글 미리보기`

### Task 4: 웹 — 상세 페이지 댓글 목록·작성·수정·삭제

**Files:**
- Create: `web/src/components/CommentSection.tsx`
- Modify: `web/src/app/(main)/posts/[id]/page.tsx` (PostCard 아래 `<CommentSection postId={id} />`)

**Interfaces:**
- Consumes: Task 1 API, Task 3 `Comment` 타입, `api()`, `useSession().me`, `Sheet`, `Confirm`, `Avatar`, `useToast`
- Produces: `export function CommentSection({ postId }: { postId: string })`

- [ ] **Step 1: 구현** — `useSWR<Comment[]>(\`/posts/${postId}/comments\`)`. 목록: Avatar + 닉네임 + 상대 시간 + 본문(줄바꿈 유지). 내 댓글만 `MoreIcon` → Sheet(수정하기/삭제하기), 삭제는 `Confirm`. 하단 고정 입력창: textarea + 등록 버튼, 200자 초과·공백이면 버튼 비활성, 180자부터 남은 글자 수 표시. 입력창은 키보드에 가리지 않게 기존 `useKeyboardViewport`/`KeyboardSafeScreen` 패턴을 따름. 등록·수정·삭제 후 댓글 목록 `mutate()` + 상세 게시글 SWR 키(`/posts/${postId}`)도 재검증. 빈 상태 문구 "아직 댓글이 없어요"
- [ ] **Step 2: 린트·빌드** — `npm run lint && npm run build`
- [ ] **Step 3: 브라우저 확인** — 두 계정(dev 로그인)으로 작성 → 상대 화면에 보임, 내 댓글만 메뉴, 수정·삭제 반영, 모바일 폭에서 입력창이 키보드 위에 붙는지
- [ ] **Step 4: 커밋** — `feat(web): 게시글 상세 댓글 목록·작성·수정·삭제`

---

**배포 메모 (계획 범위 밖, 끝나고 사용자 확인):** 운영은 `ddl-auto: update` 라 API 재배포 시 `comments` 테이블 자동 생성. 웹(Vercel)과 API 배포 순서는 API 먼저.
