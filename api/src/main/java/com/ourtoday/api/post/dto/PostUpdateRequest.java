package com.ourtoday.api.post.dto;

import com.ourtoday.api.post.Post;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record PostUpdateRequest(
    @Schema(description = "본문 (최대 300자)")
    @NotBlank @Size(max = Post.CONTENT_MAX_LENGTH) String content,

    @Schema(description = "기록 날짜 (오늘 이후 불가)")
    @NotNull LocalDate postedDate,

    @Schema(description = "이미지 전체 목록으로 교체 (최대 4개). null 이면 기존 유지, [] 면 모두 삭제")
    @Size(max = Post.MAX_IMAGES) List<@NotBlank String> imageKeys,

    @Schema(description = "장소 전체 교체. null 이면 위치 삭제")
    @Valid PlaceLocation location
) {}
