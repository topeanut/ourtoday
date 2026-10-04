package com.ourtoday.api.common.guard;

import com.ourtoday.api.common.error.BusinessException;
import com.ourtoday.api.common.error.ErrorCode;
import com.ourtoday.api.couple.Couple;
import com.ourtoday.api.couple.CoupleRepository;
import com.ourtoday.api.user.User;
import com.ourtoday.api.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.annotation.Annotation;
import java.util.UUID;

/**
 * {@link RequireProfile} / {@link RequireCouple} 이 붙은 핸들러의 온보딩 단계 검사
 */
@Component
@RequiredArgsConstructor
public class OnboardingGuardInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;
    private final CoupleRepository coupleRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        boolean requireCouple = hasAnnotation(method, RequireCouple.class);
        boolean requireProfile = requireCouple || hasAnnotation(method, RequireProfile.class);
        if (!requireProfile) {
            return true;
        }

        UUID userId = currentUserId();
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!user.isProfileCompleted()) {
            throw new BusinessException(ErrorCode.PROFILE_REQUIRED);
        }
        if (requireCouple) {
            boolean connected = coupleRepository.findByMemberUserId(userId)
                .map(Couple::isConnected)
                .orElse(false);
            if (!connected) {
                throw new BusinessException(ErrorCode.COUPLE_REQUIRED);
            }
        }
        return true;
    }

    private boolean hasAnnotation(HandlerMethod method, Class<? extends Annotation> type) {
        return method.hasMethodAnnotation(type)
            || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), type);
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
