package com.ourtoday.api.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public record PostMemoriesResponse(
    @Schema(description = "기준일 (Asia/Seoul 오늘)") LocalDate today,
    @Schema(description = "연도별 묶음 (최근 연도 먼저, 글 없는 연도는 생략)") List<YearMemories> memories
) {

    public record YearMemories(int year, List<PostResponse> posts) {}
}
