package com.ourtoday.api.user;

import com.ourtoday.api.image.dto.PresignedUrlRequest;
import com.ourtoday.api.image.dto.PresignedUrlResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    @Operation(summary = "프로필 사진 업로드 URL 발급",
        description = "커플 연결 전(가입 중)에도 사용 가능. 받은 url 로 PUT 업로드 후 key 를 PATCH /users/me 의 profileImageKey 로 보냄")
    @PostMapping("/profile-image/presigned-url")
    public PresignedUrlResponse profileImageUploadUrl(@AuthenticationPrincipal UUID userId,
                                                      @Valid @RequestBody PresignedUrlRequest request) {
        return userService.createProfileImageUploadUrl(userId, request);
    }

    @Operation(summary = "직접 올린 프로필 사진 삭제", description = "카카오 프로필 사진이 있으면 그것으로, 없으면 이름 첫 글자로 돌아감")
    @DeleteMapping("/profile-image")
    public UserResponse removeProfileImage(@AuthenticationPrincipal UUID userId) {
        return userService.removeProfileImage(userId);
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
