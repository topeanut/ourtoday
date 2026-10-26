package com.ourtoday.api.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record CoupleStatsResponse(
    @Schema(description = "내 통계") MemberStats me,
    @Schema(description = "상대 통계") MemberStats partner,
    @Schema(description = "커플 통계") CoupleSummary couple
) {

    public record MemberStats(
        @Schema(description = "누적 게시글") long totalPosts,
        @Schema(description = "이번 달 게시글 (Asia/Seoul, postedDate 기준)") long thisMonthPosts,
        @Schema(description = "연속 작성일 (오늘 아직 안 썼으면 어제부터 셈)") int consecutiveDays,
        @Schema(description = "누적 캘린더 메모") long totalMemos,
        @Schema(description = "누적 반응 남긴 수") long totalReactionsGiven
    ) {}

    public record CoupleSummary(
        @Schema(description = "사귄 지 며칠째 (사귄 날 = 1일)") long daysTogether,
        @Schema(description = "첫 게시글 날짜 (없으면 null)") LocalDate firstPostDate
    ) {}
}
