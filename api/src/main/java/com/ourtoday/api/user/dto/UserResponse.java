package com.ourtoday.api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    @Schema(description = "유저 ID") UUID id,
    @Schema(description = "닉네임 (프로필 미완료 시 빈 문자열)") String nickname,
    @Schema(description = "프로필 이미지 URL") String profileImageUrl,
    @Schema(description = "로그인 회원 식별값 (카카오 회원번호, Apple 은 apple:{식별값})") String kakaoId,
    @Schema(description = "프로필 입력 완료 여부") boolean profileCompleted,
    @Schema(description = "가입 시각") Instant createdAt,
    @Schema(description = "커플 연결 완료 여부") boolean hasCouple
) {}
