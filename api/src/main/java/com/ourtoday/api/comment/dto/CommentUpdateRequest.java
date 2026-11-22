package com.ourtoday.api.comment.dto;

import com.ourtoday.api.comment.Comment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentUpdateRequest(
    @Schema(description = "댓글 내용 (공백만 불가, 최대 200자)", example = "좋았어!")
    @NotBlank @Size(max = Comment.CONTENT_MAX_LENGTH) String content
) {}
