package com.ourtoday.api.location.dto;

import java.util.List;

public record TrackResponse(List<LatLng> path, int pointCount) {
}
