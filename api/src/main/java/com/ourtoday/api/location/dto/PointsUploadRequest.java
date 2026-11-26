package com.ourtoday.api.location.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PointsUploadRequest(@NotEmpty @Valid List<PointInput> points) {
}
