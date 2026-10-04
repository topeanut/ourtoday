package com.ourtoday.api.couple;

import com.ourtoday.api.common.error.BusinessException;
import com.ourtoday.api.common.error.ErrorCode;
import com.ourtoday.api.couple.dto.CoupleInviteResponse;
import com.ourtoday.api.couple.dto.CoupleResponse;
import com.ourtoday.api.couple.dto.CoupleResponse.CoupleMemberResponse;
import com.ourtoday.api.user.User;
import com.ourtoday.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoupleService {

    static final Duration INVITE_TTL = Duration.ofHours(24);
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_CODE_ATTEMPTS = 10;

    private final CoupleRepository coupleRepository;
    private final CoupleMemberRepository coupleMemberRepository;
    private final UserRepository userRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final Clock clock;

    /**
     * 커플 없음 → 새 PENDING 커플 생성. 이미 내 PENDING 커플이 있으면 코드만 재발급
     */
    @Transactional
    public CoupleInviteResponse createInvite(UUID userId) {
        Optional<Couple> existing = coupleRepository.findByMemberUserId(userId);
        if (existing.isPresent() && existing.get().isConnected()) {
            throw new BusinessException(ErrorCode.ALREADY_IN_COUPLE);
        }

        String code = generateUniqueCode();
        Instant expiresAt = clock.instant().plus(INVITE_TTL);

        Couple couple = existing
            .map(c -> {
                c.reissueInviteCode(code, expiresAt);
                return c;
            })
            .orElseGet(() -> {
                Couple created = coupleRepository.save(Couple.createPending(code, expiresAt));
                coupleMemberRepository.save(CoupleMember.of(created.getId(), userId));
                return created;
            });

        return new CoupleInviteResponse(couple.getInviteCode(), couple.getInviteCodeExpiresAt());
    }

    /**
     * 초대코드로 합류. 내가 만들어 둔 PENDING 커플이 있으면 정리 후 합류
     */
    @Transactional
    public CoupleResponse join(UUID userId, String rawInviteCode) {
        String code = rawInviteCode.trim().toUpperCase(Locale.ROOT);
        Couple couple = coupleRepository.findByInviteCodeForUpdate(code)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_INVALID));
        if (couple.isInviteExpired(clock.instant())) {
            throw new BusinessException(ErrorCode.INVITE_CODE_EXPIRED);
        }

        Optional<Couple> mine = coupleRepository.findByMemberUserId(userId);
        if (mine.isPresent()) {
            Couple myCouple = mine.get();
            if (myCouple.getId().equals(couple.getId())) {
                throw new BusinessException(ErrorCode.INVITE_CODE_INVALID, "본인이 만든 초대코드로는 합류할 수 없습니다.");
            }
            if (myCouple.isConnected()) {
                throw new BusinessException(ErrorCode.ALREADY_IN_COUPLE);
            }
            coupleMemberRepository.deleteByCoupleId(myCouple.getId());
            coupleRepository.delete(myCouple);
        }

        coupleMemberRepository.save(CoupleMember.of(couple.getId(), userId));
        couple.connect(LocalDate.now(clock.withZone(SERVICE_ZONE)));
        return toResponse(couple);
    }

    @Transactional(readOnly = true)
    public CoupleResponse getMyCouple(UUID userId) {
        Couple couple = coupleRepository.findByMemberUserId(userId)
            .filter(Couple::isConnected)
            .orElseThrow(() -> new BusinessException(ErrorCode.COUPLE_REQUIRED));
        return toResponse(couple);
    }

    private CoupleResponse toResponse(Couple couple) {
        List<UUID> memberIds = coupleMemberRepository.findByCoupleIdOrderByJoinedAtAsc(couple.getId()).stream()
            .map(CoupleMember::getUserId)
            .toList();
        Map<UUID, User> users = userRepository.findAllById(memberIds).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

        List<CoupleMemberResponse> members = memberIds.stream()
            .map(users::get)
            .map(u -> new CoupleMemberResponse(u.getId(), u.getNickname(), u.getProfileImageUrl()))
            .toList();
        return new CoupleResponse(couple.getId(), couple.getStartedAt(), couple.getStatus(), members);
    }

    private String generateUniqueCode() {
        for (int i = 0; i < MAX_CODE_ATTEMPTS; i++) {
            String code = inviteCodeGenerator.generate();
            if (!coupleRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("초대코드 생성 실패: 충돌 반복");
    }
}
