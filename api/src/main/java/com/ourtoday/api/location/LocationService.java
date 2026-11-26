package com.ourtoday.api.location;

import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.location.dto.SharingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationShareRepository shareRepository;
    private final LocationShareEventRepository eventRepository;
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

    private SharingResponse response(UUID coupleId, UUID userId, boolean mine) {
        UUID partnerId = coupleService.getPartnerId(coupleId, userId);
        return new SharingResponse(mine, isSharing(partnerId));
    }
}
