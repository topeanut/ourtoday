package com.ourtoday.api.push.dto;

import com.ourtoday.api.push.PushToken;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushTokenDeleteRequest(
    @Schema(description = "FCM 푸시 토큰") @NotBlank @Size(max = PushToken.TOKEN_MAX_LENGTH) String token
) {
}
