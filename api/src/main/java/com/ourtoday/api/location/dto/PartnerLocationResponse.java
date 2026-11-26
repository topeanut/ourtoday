package com.ourtoday.api.location.dto;

import java.time.Instant;

/** latest/capturedAt 은 상대가 공유 중이고 점이 있을 때만 값이 있다 (그 외 null) */
public record PartnerLocationResponse(boolean partnerSharing, LatLng latest, Instant capturedAt,
                                      boolean preciseActive) {
}
