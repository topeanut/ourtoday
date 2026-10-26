package com.ourtoday.api.user;

import com.ourtoday.api.user.dto.OnboardingStatusResponse;
import com.ourtoday.api.user.dto.UserResponse;
import com.ourtoday.api.user.dto.UserSettingsResponse;
import com.ourtoday.api.user.dto.UserSettingsUpdateRequest;
import com.ourtoday.api.user.dto.UserUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "User", description = "내 프로필 / 온보딩")
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 프로필")
    @GetMapping
    public UserResponse me(@AuthenticationPrincipal UUID userId) {
        return userService.getMe(userId);
    }

    @Operation(summary = "프로필 수정", description = "첫 호출 시 가입 완료(profileCompleted=true) 처리")
    @PatchMapping
    public UserResponse update(@AuthenticationPrincipal UUID userId, @Valid @RequestBody UserUpdateRequest request) {
        return userService.updateMe(userId, request);
    }

    @Operation(summary = "온보딩 단계")
    @GetMapping("/onboarding-status")
    public OnboardingStatusResponse onboardingStatus(@AuthenticationPrincipal UUID userId) {
        return userService.getOnboardingStatus(userId);
    }

    @Operation(summary = "알림 설정 조회")
    @GetMapping("/settings")
    public UserSettingsResponse settings(@AuthenticationPrincipal UUID userId) {
        return userService.getSettings(userId);
    }

    @Operation(summary = "알림 설정 수정")
    @PatchMapping("/settings")
    public UserSettingsResponse updateSettings(@AuthenticationPrincipal UUID userId,
                                               @Valid @RequestBody UserSettingsUpdateRequest request) {
        return userService.updateSettings(userId, request);
    }
}
