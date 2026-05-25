# API 작성 컨벤션

> 이 문서는 새 API(컨트롤러/DTO) 작성 시 반드시 따라야 할 규칙. 본 프로젝트의 모든 API는 이 컨벤션에 맞춰 작성합니다.

---

## 1. 패키지 구조

```
com.ourtoday.api.<리소스>/
  ├── <리소스>Controller.java
  ├── <리소스>Service.java
  ├── <리소스>Repository.java
  ├── <리소스>.java               (Entity)
  └── dto/
      ├── <리소스>CreateRequest.java
      ├── <리소스>UpdateRequest.java
      └── <리소스>Response.java
```

예시: `posts` 도메인이면
```
com.ourtoday.api.posts/
  ├── PostController.java
  ├── PostService.java
  ├── PostRepository.java
  ├── Post.java
  └── dto/
      ├── PostCreateRequest.java
      ├── PostUpdateRequest.java
      └── PostResponse.java
```

---

## 2. 컨트롤러

- `@RestController` + `@RequestMapping("/api/v1/<리소스>")`
- 생성자 주입 — Lombok `@RequiredArgsConstructor` + `private final`
- 메서드는 얇게. 로직은 Service 계층에
- HTTP 메서드 매핑: `@GetMapping`, `@PostMapping`, `@PatchMapping`, `@DeleteMapping`
- URL은 `kebab-case` (`/bucket-items`), 자바 코드는 `camelCase`

예시:
```java
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {
    private final PostService postService;

    @PostMapping
    public PostResponse create(@Valid @RequestBody PostCreateRequest request) {
        return postService.create(request);
    }
}
```

---

## 3. DTO

- **모든 요청/응답 DTO는 record로 작성** (Java 21 record)
- 위치: `com.ourtoday.api.<리소스>.dto`
- 명명:
  - 요청: `<리소스>CreateRequest`, `<리소스>UpdateRequest`, `<리소스>JoinRequest` 등 액션 명시
  - 응답: `<리소스>Response`, `<리소스>SummaryResponse` 등
- **요청 DTO에는 validation 어노테이션 필수**: `@NotBlank`, `@NotNull`, `@Size`, `@Email` 등
- 컨트롤러에서는 `@Valid` 로 발동

예시:
```java
public record PostCreateRequest(
    @NotBlank @Size(max = 300) String content,
    @NotNull LocalDate postedDate,
    List<String> imageUrls
) {}

public record PostResponse(
    UUID id,
    String content,
    LocalDate postedDate,
    Instant createdAt
) {}
```

---

## 4. 응답 형식

### 성공
- 도메인 record 그대로 반환 (Spring이 자동 JSON 변환)
- 상태 코드:
  - 조회: 200 OK
  - 생성: 201 Created (또는 200, 일관성만 유지)
  - 수정: 200 OK
  - 삭제: 204 No Content (`void` 반환)

### 실패
- 표준 `ErrorResponse` 형식 (글로벌 ExceptionHandler에서 생성)
- 에러 코드는 **`API.md`의 "표준 에러 코드" 목록에서만** 사용
- 새 에러 코드 필요 시 `API.md` 먼저 업데이트

```json
{
  "error": {
    "code": "COUPLE_REQUIRED",
    "message": "커플 연결이 필요한 기능입니다.",
    "timestamp": "2026-05-25T10:23:45Z"
  }
}
```

### 페이징
- Spring Data `Page<T>` 그대로 반환
- 쿼리 파라미터: `?page=0&size=20`

---

## 5. 권한/인증 가드

### 인증 필요
- 기본값. Spring Security 필터가 처리

### 커플 매칭 필요 (Phase 1 이후 도입 예정)
- 커스텀 어노테이션 `@RequireCouple`을 메서드에 부착
- 미매칭 유저 호출 시 `COUPLE_REQUIRED` (403)

### 본인/같은 커플 데이터인지 체크
- Service 계층에서 명시적으로 검증
- 위반 시 `FORBIDDEN` (403)

---

## 6. Swagger (springdoc)

- 요청/응답을 **record로 만들면 자동으로 Schema 등록**
- 추가 설명: 필드/클래스에 `@Schema(description = "...")`
- 컨트롤러/메서드: `@Tag`, `@Operation`, `@ApiResponse` 등 선택적으로

예시:
```java
public record PostResponse(
    @Schema(description = "게시글 ID") UUID id,
    @Schema(description = "본문 (최대 300자)") String content
) {}
```

---

## 7. 트랜잭션

- Service 메서드에 `@Transactional` (쓰기) / `@Transactional(readOnly = true)` (조회)
- Controller에는 절대 붙이지 않음

---

## 8. 신규 API 추가 시 체크리스트

API 새로 만들 때 이 순서로:

1. `API.md`에 엔드포인트 추가 (Method, Path, 설명, 요청/응답 예시)
2. Entity 클래스 작성 (필요 시)
3. Repository 인터페이스 작성
4. Request/Response DTO 작성 (record + validation)
5. Service 작성 (로직 + 트랜잭션 + 권한 체크)
6. Controller 작성 (얇게, Service 위임)
7. Swagger UI에서 자동 등록 확인 (`/swagger-ui/index.html`)
8. `.http` 파일에 테스트 요청 추가
9. (선택) 통합 테스트 작성
