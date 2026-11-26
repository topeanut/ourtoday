package com.ourtoday.api.location.dto;

import jakarta.validation.constraints.NotNull;

public record SharingRequest(@NotNull Boolean enabled) {
}
