package com.ourtoday.api.push;

import com.ourtoday.api.push.dto.PushTokenDeleteRequest;
import com.ourtoday.api.push.dto.PushTokenRegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Push", description = "푸시 알림 토큰")
@RestController
@RequestMapping("/api/v1/push-tokens")
@RequiredArgsConstructor
public class PushTokenController {

    private final PushTokenService pushTokenService;

    @Operation(summary = "푸시 토큰 등록 (이미 있으면 계정·플랫폼 갱신)")
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@AuthenticationPrincipal UUID userId, @Valid @RequestBody PushTokenRegisterRequest request) {
        pushTokenService.register(userId, request.token(), request.platform());
    }

    @Operation(summary = "푸시 토큰 삭제 (내 토큰이 아니면 무시)")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID userId, @Valid @RequestBody PushTokenDeleteRequest request) {
        pushTokenService.delete(userId, request.token());
    }
}
