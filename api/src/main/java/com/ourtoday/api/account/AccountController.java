package com.ourtoday.api.account;

import com.ourtoday.api.account.dto.ExportResponse;
import com.ourtoday.api.common.guard.RequireProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Account", description = "데이터 내보내기 / 탈퇴")
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class AccountController {

    private final ExportService exportService;
    private final AccountService accountService;

    @Operation(summary = "데이터 내보내기", description = "우리 커플 기록(data.json)과 사진을 zip 으로. 응답 URL 은 1시간 유효")
    @RequireProfile
    @PostMapping("/export")
    public ExportResponse export(@AuthenticationPrincipal UUID userId) {
        return exportService.export(userId);
    }

    @Operation(summary = "회원 탈퇴", description = "커플 해제 + 내가 쓴 글·사진·반응·댓글·메모·계정 삭제. 되돌릴 수 없음")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal UUID userId) {
        accountService.withdraw(userId);
    }
}
