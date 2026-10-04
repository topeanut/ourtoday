package com.ourtoday.api.couple.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CoupleJoinRequest(
    @Schema(description = "상대가 공유한 초대코드 (대소문자 무관)", example = "ABC234")
    @NotBlank @Size(max = 8) String inviteCode
) {}
