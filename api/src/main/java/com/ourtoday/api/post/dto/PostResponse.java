package com.ourtoday.api.post.dto;

import com.ourtoday.api.comment.dto.CommentResponse;
import com.ourtoday.api.reaction.EmojiType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PostResponse(
    @Schema(description = "게시글 ID") UUID id,
    @Schema(description = "작성자") Author author,
    @Schema(description = "본문") String content,
    @Schema(description = "기록 날짜") LocalDate postedDate,
    @Schema(description = "이미지 (표시 순서)") List<Image> images,
    @Schema(description = "첨부 장소 (없으면 null)") PlaceLocation location,
    @Schema(description = "반응 집계 (1개 이상인 이모지만, HEART→FIRE 순)") List<Reaction> reactions,
    @Schema(description = "댓글 총 개수") long commentCount,
    @Schema(description = "최근 댓글 최대 2개 (오래된 순)") List<CommentResponse> recentComments,
    @Schema(description = "작성 시각") Instant createdAt,
    @Schema(description = "수정 시각") Instant updatedAt
) {

    public record Author(
        UUID id,
        String nickname,
        String profileImageUrl
    ) {}

    public record Image(
        @Schema(description = "임시 조회 URL (1시간 유효)") String url,
        @Schema(description = "수정 시 imageKeys 에 다시 넣을 값") String imageKey,
        int order
    ) {}

    public record Reaction(
        EmojiType emojiType,
        long count,
        @Schema(description = "내가 누른 반응인지") boolean reactedByMe
    ) {}
}
