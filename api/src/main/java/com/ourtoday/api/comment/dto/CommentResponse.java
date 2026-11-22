package com.ourtoday.api.comment.dto;

import com.ourtoday.api.comment.Comment;
import com.ourtoday.api.post.dto.PostResponse;
import com.ourtoday.api.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
    @Schema(description = "댓글 ID") UUID id,
    @Schema(description = "작성자") PostResponse.Author author,
    @Schema(description = "내용") String content,
    @Schema(description = "작성 시각") Instant createdAt,
    @Schema(description = "수정 시각") Instant updatedAt
) {

    public static CommentResponse of(Comment comment, User author, String authorProfileImageUrl) {
        return new CommentResponse(
            comment.getId(),
            new PostResponse.Author(author.getId(), author.getNickname(), authorProfileImageUrl),
            comment.getContent(),
            comment.getCreatedAt(),
            comment.getUpdatedAt()
        );
    }
}
