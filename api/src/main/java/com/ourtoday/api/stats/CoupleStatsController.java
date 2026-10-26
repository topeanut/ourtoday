package com.ourtoday.api.stats;

import com.ourtoday.api.common.guard.RequireCouple;
import com.ourtoday.api.stats.dto.CoupleStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Stats", description = "활동 통계")
@RestController
@RequestMapping("/api/v1/couples/me/stats")
@RequiredArgsConstructor
@RequireCouple
public class CoupleStatsController {

    private final CoupleStatsService coupleStatsService;

    @Operation(summary = "두 사람의 작성 통계")
    @GetMapping
    public CoupleStatsResponse stats(@AuthenticationPrincipal UUID userId) {
        return coupleStatsService.getStats(userId);
    }
}
