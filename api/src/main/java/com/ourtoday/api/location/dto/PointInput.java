package com.ourtoday.api.location.dto;

import com.ourtoday.api.location.PointSource;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record PointInput(
    @DecimalMin("-90") @DecimalMax("90") double lat,
    @DecimalMin("-180") @DecimalMax("180") double lng,
    Double accuracy,
    @NotNull Instant capturedAt,
    @NotNull PointSource source
) {
}
