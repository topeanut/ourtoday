package com.ourtoday.api.couple;

import com.ourtoday.api.common.guard.RequireCouple;
import com.ourtoday.api.common.guard.RequireProfile;
import com.ourtoday.api.couple.dto.CoupleInviteResponse;
import com.ourtoday.api.couple.dto.CoupleJoinRequest;
import com.ourtoday.api.couple.dto.CoupleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Couple", description = "커플 연결")
@RestController
@RequestMapping("/api/v1/couples")
@RequiredArgsConstructor
public class CoupleController {

    private final CoupleService coupleService;

    @Operation(summary = "초대코드 생성", description = "이미 대기 중인 초대코드가 있으면 새 코드로 교체")
    @RequireProfile
    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public CoupleInviteResponse invite(@AuthenticationPrincipal UUID userId) {
        return coupleService.createInvite(userId);
    }

    @Operation(summary = "초대코드로 합류")
    @RequireProfile
    @PostMapping("/join")
    public CoupleResponse join(@AuthenticationPrincipal UUID userId, @Valid @RequestBody CoupleJoinRequest request) {
        return coupleService.join(userId, request.inviteCode());
    }

    @Operation(summary = "내 커플 정보")
    @RequireCouple
    @GetMapping("/me")
    public CoupleResponse me(@AuthenticationPrincipal UUID userId) {
        return coupleService.getMyCouple(userId);
    }
}
