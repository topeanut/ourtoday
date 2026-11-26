package com.ourtoday.api.location;

import com.ourtoday.api.common.error.BusinessException;
import com.ourtoday.api.common.error.ErrorCode;
import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.location.dto.LatLng;
import com.ourtoday.api.location.dto.PartnerLocationResponse;
import com.ourtoday.api.location.dto.PointsUploadRequest;
import com.ourtoday.api.location.dto.SharingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationShareRepository shareRepository;
    private final LocationShareEventRepository eventRepository;
    private final LocationPointRepository pointRepository;
    private final CoupleService coupleService;
    private final Clock clock;

    /** 실제로 상태가 바뀔 때만 이력 append (같은 값 PUT 은 무시) */
    @Transactional
    public SharingResponse setSharing(UUID userId, boolean enabled) {
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        LocationShare share = shareRepository.findById(userId).orElse(null);
        boolean previous = share != null && share.isEnabled();
        if (share == null) {
            shareRepository.save(LocationShare.of(userId, enabled));
        } else {
            share.changeTo(enabled);
        }
        if (previous != enabled) {
            eventRepository.save(LocationShareEvent.of(userId, enabled, clock.instant()));
        }
        return response(coupleId, userId, enabled);
    }

    @Transactional(readOnly = true)
    public SharingResponse getSharing(UUID userId) {
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        return response(coupleId, userId, isSharing(userId));
    }

    @Transactional(readOnly = true)
    public boolean isSharing(UUID userId) {
        return shareRepository.findById(userId).map(LocationShare::isEnabled).orElse(false);
    }

    /** 해당 시각 이전(포함) 가장 최근 이벤트로 판정. 이벤트가 없으면 false */
    @Transactional(readOnly = true)
    public boolean wasSharingAt(UUID userId, Instant at) {
        return eventRepository.findTopByUserIdAndAtLessThanEqualOrderByAtDesc(userId, at)
            .map(LocationShareEvent::isEnabled)
            .orElse(false);
    }

    /** 현재 공유 OFF 면 거부. ON 이면 캡처 시각에 공유 중이던 점만 저장(나머지는 조용히 버림) */
    @Transactional
    public void uploadPoints(UUID userId, PointsUploadRequest request) {
        if (!isSharing(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "위치 공유가 꺼져 있습니다.");
        }
        List<LocationPoint> kept = request.points().stream()
            .filter(p -> wasSharingAt(userId, p.capturedAt()))
            .map(p -> LocationPoint.of(userId, p.lat(), p.lng(), p.accuracy(), p.capturedAt(), p.source()))
            .toList();
        pointRepository.saveAll(kept);
    }

    /** 상대가 공유 중일 때만 최신 점을 준다. OFF 면 점이 남아 있어도 노출하지 않음 */
    @Transactional(readOnly = true)
    public PartnerLocationResponse getPartnerLocation(UUID userId) {
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        UUID partnerId = coupleService.getPartnerId(coupleId, userId);
        if (!isSharing(partnerId)) {
            return new PartnerLocationResponse(false, null, null, false);
        }
        // TODO(Task 5): hasActivePreciseRequest(partnerId)
        boolean preciseActive = false;
        return pointRepository.findTopByUserIdOrderByCapturedAtDesc(partnerId)
            .map(p -> new PartnerLocationResponse(true, new LatLng(p.getLat(), p.getLng()),
                p.getCapturedAt(), preciseActive))
            .orElseGet(() -> new PartnerLocationResponse(true, null, null, preciseActive));
    }

    private SharingResponse response(UUID coupleId, UUID userId, boolean mine) {
        UUID partnerId = coupleService.getPartnerId(coupleId, userId);
        return new SharingResponse(mine, isSharing(partnerId));
    }
}
