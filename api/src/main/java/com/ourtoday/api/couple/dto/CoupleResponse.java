package com.ourtoday.api.couple.dto;

import com.ourtoday.api.couple.CoupleStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CoupleResponse(
    @Schema(description = "커플 ID") UUID id,
    @Schema(description = "사귄 날짜 (기본값: 연결된 날)") LocalDate startedAt,
    @Schema(description = "커플 상태") CoupleStatus status,
    @Schema(description = "멤버 (합류 순)") List<CoupleMemberResponse> members
) {

    public record CoupleMemberResponse(
        UUID id,
        String nickname,
        String profileImageUrl
    ) {}
}
