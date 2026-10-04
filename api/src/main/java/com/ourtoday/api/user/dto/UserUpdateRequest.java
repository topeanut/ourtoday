package com.ourtoday.api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
    @Schema(description = "닉네임", example = "민지")
    @NotBlank @Size(max = 20) String nickname,

    @Schema(description = "프로필 이미지 URL (없으면 null)")
    @Size(max = 500) String profileImageUrl
) {}
