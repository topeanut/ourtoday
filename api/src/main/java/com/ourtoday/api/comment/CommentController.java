package com.ourtoday.api.comment;

import com.ourtoday.api.comment.dto.CommentCreateRequest;
import com.ourtoday.api.comment.dto.CommentUpdateRequest;
import com.ourtoday.api.comment.dto.CommentResponse;
import com.ourtoday.api.common.guard.RequireCouple;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Comment", description = "게시글 댓글 (대댓글 없음)")
@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
@RequiredArgsConstructor
@RequireCouple
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "댓글 목록", description = "오래된 순 (createdAt asc)")
    @GetMapping
    public List<CommentResponse> list(@AuthenticationPrincipal UUID userId, @PathVariable UUID postId) {
        return commentService.list(userId, postId);
    }

    @Operation(summary = "댓글 작성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(
        @AuthenticationPrincipal UUID userId,
        @PathVariable UUID postId,
        @Valid @RequestBody CommentCreateRequest request
    ) {
        return commentService.create(userId, postId, request.content());
    }

    @Operation(summary = "댓글 수정", description = "작성자 본인만")
    @PatchMapping("/{commentId}")
    public CommentResponse update(
        @AuthenticationPrincipal UUID userId,
        @PathVariable UUID postId,
        @PathVariable UUID commentId,
        @Valid @RequestBody CommentUpdateRequest request
    ) {
        return commentService.update(userId, postId, commentId, request.content());
    }

    @Operation(summary = "댓글 삭제", description = "작성자 본인만")
    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID postId, @PathVariable UUID commentId) {
        commentService.delete(userId, postId, commentId);
    }
}
