package com.ourtoday.api.post;

import com.ourtoday.api.common.guard.RequireCouple;
import com.ourtoday.api.post.dto.PostCreateRequest;
import com.ourtoday.api.post.dto.PostResponse;
import com.ourtoday.api.post.dto.PostUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Post", description = "3줄 일기")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@RequireCouple
public class PostController {

    private final PostService postService;

    @Operation(summary = "작성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse create(@AuthenticationPrincipal UUID userId, @Valid @RequestBody PostCreateRequest request) {
        return postService.create(userId, request);
    }

    @Operation(summary = "우리 커플 게시글 목록",
        description = "정렬: sort=postedDate,desc(기본) | postedDate,asc | createdAt,desc | createdAt,asc")
    @GetMapping
    public Page<PostResponse> list(
        @AuthenticationPrincipal UUID userId,
        @Parameter(description = "me / partner (생략 시 전체)") @RequestParam(required = false) String author,
        @Parameter(description = "posted_date 시작 (YYYY-MM-DD)")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @Parameter(description = "posted_date 끝 (YYYY-MM-DD)")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @ParameterObject @PageableDefault(size = 20, sort = "postedDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return postService.list(userId, author, from, to, pageable);
    }

    @Operation(summary = "상세 (공유 링크)")
    @GetMapping("/{id}")
    public PostResponse get(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return postService.get(userId, id);
    }

    @Operation(summary = "수정 (작성자만)")
    @PatchMapping("/{id}")
    public PostResponse update(
        @AuthenticationPrincipal UUID userId,
        @PathVariable UUID id,
        @Valid @RequestBody PostUpdateRequest request
    ) {
        return postService.update(userId, id, request);
    }

    @Operation(summary = "삭제 (작성자만)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        postService.delete(userId, id);
    }
}
