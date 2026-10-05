package com.ourtoday.api.post.dto;

import com.ourtoday.api.post.Post;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PostUpdateRequest(
    @Schema(description = "본문 (최대 300자)")
    @NotBlank @Size(max = Post.CONTENT_MAX_LENGTH) String content,

    @Schema(description = "기록 날짜 (오늘 이후 불가)")
    @NotNull LocalDate postedDate
) {}
