package com.ourtoday.api.location;

import com.ourtoday.api.common.guard.RequireCouple;
import com.ourtoday.api.location.dto.PartnerLocationResponse;
import com.ourtoday.api.location.dto.PointsUploadRequest;
import com.ourtoday.api.location.dto.SharingRequest;
import com.ourtoday.api.location.dto.SharingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Location", description = "실시간 위치 공유")
@RestController
@RequestMapping("/api/v1/location")
@RequiredArgsConstructor
@RequireCouple
public class LocationController {

    private final LocationService locationService;

    @Operation(summary = "내 위치 공유 ON/OFF")
    @PutMapping("/sharing")
    public SharingResponse setSharing(@AuthenticationPrincipal UUID userId,
                                      @Valid @RequestBody SharingRequest request) {
        return locationService.setSharing(userId, request.enabled());
    }

    @Operation(summary = "나/상대 위치 공유 상태")
    @GetMapping("/sharing")
    public SharingResponse getSharing(@AuthenticationPrincipal UUID userId) {
        return locationService.getSharing(userId);
    }

    @Operation(summary = "위치 포인트 배치 업로드 (공유 OFF 구간 점은 버림)")
    @PostMapping("/points")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadPoints(@AuthenticationPrincipal UUID userId,
                             @Valid @RequestBody PointsUploadRequest request) {
        locationService.uploadPoints(userId, request);
    }

    @Operation(summary = "상대 최신 위치 (상대가 공유 중일 때만)")
    @GetMapping("/partner")
    public PartnerLocationResponse getPartner(@AuthenticationPrincipal UUID userId) {
        return locationService.getPartnerLocation(userId);
    }
}
