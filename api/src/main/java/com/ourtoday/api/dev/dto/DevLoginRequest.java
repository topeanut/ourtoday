package com.ourtoday.api.dev.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DevLoginRequest(
    @Schema(description = "개발용 계정 이름 (같은 이름이면 같은 계정)", example = "qa-a")
    @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_-]{1,20}$", message = "영문·숫자·-·_ 20자 이내") String name
) {}
