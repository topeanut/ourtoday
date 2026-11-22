package com.ourtoday.api.user;

import com.ourtoday.api.common.error.BusinessException;
import com.ourtoday.api.common.error.ErrorCode;
import com.ourtoday.api.couple.Couple;
import com.ourtoday.api.couple.CoupleRepository;
import com.ourtoday.api.user.dto.OnboardingStatusResponse;
import com.ourtoday.api.user.dto.UserResponse;
import com.ourtoday.api.user.dto.UserSettingsResponse;
import com.ourtoday.api.user.dto.UserSettingsUpdateRequest;
import com.ourtoday.api.user.dto.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final CoupleRepository coupleRepository;

    static final String APPLE_ID_PREFIX = "apple:";

    @Transactional
    public User findOrCreateByKakaoId(String kakaoId) {
        return userRepository.findByKakaoId(kakaoId)
            .orElseGet(() -> {
                User user = userRepository.save(User.createFromKakao(kakaoId));
                userSettingsRepository.save(UserSettings.createDefault(user));
                return user;
            });
    }

    /**
     * Apple 회원은 kakao_id 컬럼에 "apple:{sub}" 로 저장 (카카오 회원번호는 숫자라 겹치지 않음).
     * refresh token 을 새로 받았으면 교체 (재로그인 때마다 Apple 이 새로 줌)
     */
    @Transactional
    public User findOrCreateByApple(String appleSubject, String appleRefreshToken, String appleClientId) {
        User user = findOrCreateByKakaoId(APPLE_ID_PREFIX + appleSubject);
        if (appleRefreshToken != null) {
            user.updateAppleToken(appleRefreshToken, appleClientId);
        }
        return user;
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(UUID userId) {
        return toResponse(getUser(userId));
    }

    /**
     * 첫 호출(프로필 미완료)은 가입 완료 처리, 이후는 일반 수정
     */
    @Transactional
    public UserResponse updateMe(UUID userId, UserUpdateRequest request) {
        User user = getUser(userId);
        String nickname = request.nickname().trim();
        if (user.isProfileCompleted()) {
            user.updateProfile(nickname, request.profileImageUrl());
        } else {
            user.completeProfile(nickname, request.profileImageUrl());
        }
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public OnboardingStatusResponse getOnboardingStatus(UUID userId) {
        User user = getUser(userId);
        if (!user.isProfileCompleted()) {
            return new OnboardingStatusResponse(OnboardingStatus.NEED_PROFILE);
        }
        OnboardingStatus status = coupleRepository.findByMemberUserId(userId)
            .map(c -> c.isConnected() ? OnboardingStatus.CONNECTED : OnboardingStatus.PENDING_INVITE)
            .orElse(OnboardingStatus.NO_COUPLE);
        return new OnboardingStatusResponse(status);
    }

    @Transactional
    public UserSettingsResponse getSettings(UUID userId) {
        return toResponse(getOrCreateSettings(userId));
    }

    @Transactional
    public UserSettingsResponse updateSettings(UUID userId, UserSettingsUpdateRequest request) {
        UserSettings settings = getOrCreateSettings(userId);
        settings.updateNotificationSettings(request.notifyMemoryToday(), request.notifyPartnerPost(),
            request.notifyReaction());
        return toResponse(settings);
    }

    /** 설정 행이 없던 초기 가입자 대비 */
    private UserSettings getOrCreateSettings(UUID userId) {
        return userSettingsRepository.findById(userId)
            .orElseGet(() -> userSettingsRepository.save(UserSettings.createDefault(getUser(userId))));
    }

    private UserSettingsResponse toResponse(UserSettings settings) {
        return new UserSettingsResponse(settings.isNotifyMemoryToday(), settings.isNotifyPartnerPost(),
            settings.isNotifyReaction());
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private UserResponse toResponse(User user) {
        Optional<Couple> couple = coupleRepository.findByMemberUserId(user.getId());
        return new UserResponse(
            user.getId(),
            user.getNickname(),
            user.getProfileImageUrl(),
            user.getKakaoId(),
            user.isProfileCompleted(),
            user.getCreatedAt(),
            couple.map(Couple::isConnected).orElse(false)
        );
    }
}
