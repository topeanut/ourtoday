package com.ourtoday.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AppleLoginRequest(
    @Schema(description = "Apple identity token (JWT)") @NotBlank String identityToken,
    @Schema(description = "Apple 인가 코드 (탈퇴 때 철회할 refresh token 발급용, 없어도 로그인은 됨)") String authorizationCode
) {}
