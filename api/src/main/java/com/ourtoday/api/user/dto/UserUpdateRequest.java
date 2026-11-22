package com.ourtoday.api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
    @Schema(description = "닉네임", example = "민지")
    @NotBlank @Size(max = 20) String nickname,

    @Schema(description = "직접 올린 프로필 사진 키 (POST /users/me/profile-image/presigned-url 로 받은 key). null 이면 사진은 그대로")
    @Size(max = 200) String profileImageKey
) {}
