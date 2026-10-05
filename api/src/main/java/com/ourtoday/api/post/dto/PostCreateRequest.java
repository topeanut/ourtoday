package com.ourtoday.api.post.dto;

import com.ourtoday.api.post.Post;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record PostCreateRequest(
    @Schema(description = "본문 (최대 300자)", example = "오늘 처음 같이 본 영화")
    @NotBlank @Size(max = Post.CONTENT_MAX_LENGTH) String content,

    @Schema(description = "기록 날짜 (오늘 이후 불가, Asia/Seoul 기준)", example = "2026-05-25")
    @NotNull LocalDate postedDate,

    @Schema(description = "업로드 완료한 imageKey 목록 (표시 순서대로, 최대 4개)")
    @Size(max = Post.MAX_IMAGES) List<@NotBlank String> imageKeys
) {}
