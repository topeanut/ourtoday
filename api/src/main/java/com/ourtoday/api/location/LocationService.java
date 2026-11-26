package com.ourtoday.api.location;

import com.ourtoday.api.common.error.BusinessException;
import com.ourtoday.api.common.error.ErrorCode;
import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.couple.CoupleUnlinkingEvent;
import com.ourtoday.api.location.dto.LatLng;
import com.ourtoday.api.location.dto.PartnerLocationResponse;
import com.ourtoday.api.location.dto.PointsUploadRequest;
import com.ourtoday.api.location.dto.SharingResponse;
import com.ourtoday.api.location.dto.TrackResponse;
import com.ourtoday.api.push.DataPush;
import com.ourtoday.api.push.PushDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocationService {

    static final Duration PRECISE_TTL = Duration.ofMinutes(3);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LocationShareRepository shareRepository;
    private final LocationShareEventRepository eventRepository;
    private final LocationPointRepository pointRepository;
    private final LocationDailyRepository dailyRepository;
    private final PreciseRequestRepository preciseRequestRepository;
    private final LocationAccessLogRepository accessLogRepository;
    private final PushDispatcher pushDispatcher;
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
        boolean preciseActive = hasActivePreciseRequest(partnerId);
        return pointRepository.findTopByUserIdOrderByCapturedAtDesc(partnerId)
            .map(p -> new PartnerLocationResponse(true, new LatLng(p.getLat(), p.getLng()),
                p.getCapturedAt(), preciseActive))
            .orElseGet(() -> new PartnerLocationResponse(true, null, null, preciseActive));
    }

    /** 상대 일자(KST) 경로. 상대가 비공유면 없는 날과 구분되지 않도록 빈 경로. 요약이 있으면 polyline, 없으면 raw */
    @Transactional(readOnly = true)
    public TrackResponse getTrack(UUID userId, LocalDate date) {
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        UUID partnerId = coupleService.getPartnerId(coupleId, userId);
        if (!isSharing(partnerId)) {
            return new TrackResponse(List.of(), 0);
        }
        return dailyRepository.findByUserIdAndDate(partnerId, date)
            .map(d -> new TrackResponse(PolylineSimplifier.decode(d.getPolyline()), d.getPointCount()))
            .orElseGet(() -> {
                List<LatLng> path = pointRepository
                    .findByUserIdAndCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtAsc(
                        partnerId, date.atStartOfDay(KST).toInstant(), date.plusDays(1).atStartOfDay(KST).toInstant())
                    .stream().map(p -> new LatLng(p.getLat(), p.getLng())).toList();
                return new TrackResponse(path, path.size());
            });
    }

    /** 활성 요청이면 만료만 연장(멱등), 아니면 새로 생성. 성공마다 접근로그 1행 + 상대에게 무음 푸시(매번, 깨우기 신뢰성) */
    @Transactional
    public void requestPrecise(UUID userId) {
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        UUID partnerId = coupleService.getPartnerId(coupleId, userId);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(PRECISE_TTL);
        List<PreciseRequest> active = preciseRequestRepository
            .findByRequesterIdAndTargetIdAndExpiresAtAfter(userId, partnerId, now);
        if (active.isEmpty()) {
            preciseRequestRepository.save(PreciseRequest.of(userId, partnerId, expiresAt));
        } else {
            active.forEach(r -> r.expireAt(expiresAt));
        }
        accessLogRepository.save(LocationAccessLog.of(userId, partnerId, AccessKind.PRECISE, now));
        pushDispatcher.sendDataToUsers(List.of(partnerId),
            new DataPush("location_precise_on", Map.of("expiresAt", expiresAt.toString())));
    }

    /** 내가 건 활성 요청을 즉시 만료하고 상대에게 off 푸시 */
    @Transactional
    public void releasePrecise(UUID userId) {
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        UUID partnerId = coupleService.getPartnerId(coupleId, userId);
        Instant now = clock.instant();
        preciseRequestRepository.findByRequesterIdAndTargetIdAndExpiresAtAfter(userId, partnerId, now)
            .forEach(r -> r.expireAt(now));
        pushDispatcher.sendDataToUsers(List.of(partnerId), new DataPush("location_precise_off", Map.of()));
    }

    @Transactional(readOnly = true)
    public boolean hasActivePreciseRequest(UUID targetUserId) {
        return preciseRequestRepository.existsByTargetIdAndExpiresAtAfter(targetUserId, clock.instant());
    }

    /**
     * 커플 해제 직전(동기 이벤트, 같은 트랜잭션)에 호출돼 상대가 아직 조회되는 시점에 정리한다.
     * 일자 요약은 본인 기록이라 유지(탈퇴 때만 삭제), 접근 로그는 법적 기록이라 항상 유지
     */
    @EventListener
    @Transactional
    public void onCoupleUnlinking(CoupleUnlinkingEvent event) {
        purgeOnUnlink(event.userId());
    }

    @Transactional
    public void purgeOnUnlink(UUID userId) {
        purge(userId, false);
    }

    /** 탈퇴는 완전 삭제: 해제 정리 + 일자 요약. 호출 시점엔 이미 커플이 해제돼 있을 수 있어 off 푸시는 건너뜀 */
    @Transactional
    public void purgeOnWithdraw(UUID userId) {
        purge(userId, true);
    }

    private void purge(UUID userId, boolean deleteSummaries) {
        // 상대 조회는 커플이 끊기기 전에만 가능하므로 off 푸시를 먼저 보낸다 (커플 없으면 생략)
        coupleService.findPartnerId(userId).ifPresent(partnerId ->
            pushDispatcher.sendDataToUsers(List.of(partnerId), new DataPush("location_precise_off", Map.of())));
        shareRepository.findById(userId).ifPresent(share -> {
            if (share.isEnabled()) {
                share.changeTo(false);
                eventRepository.save(LocationShareEvent.of(userId, false, clock.instant()));
            }
        });
        pointRepository.deleteByUserId(userId);
        preciseRequestRepository.deleteByRequesterIdOrTargetId(userId, userId);
        if (deleteSummaries) {
            dailyRepository.deleteByUserId(userId);
        }
    }

    private SharingResponse response(UUID coupleId, UUID userId, boolean mine) {
        UUID partnerId = coupleService.getPartnerId(coupleId, userId);
        return new SharingResponse(mine, isSharing(partnerId));
    }
}
