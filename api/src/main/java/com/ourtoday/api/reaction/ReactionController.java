package com.ourtoday.api.reaction;

import com.ourtoday.api.common.guard.RequireCouple;
import com.ourtoday.api.reaction.dto.ReactionToggleRequest;
import com.ourtoday.api.reaction.dto.ReactionToggleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Reaction", description = "게시글 반응 (이모지)")
@RestController
@RequestMapping("/api/v1/posts/{postId}/reactions")
@RequiredArgsConstructor
@RequireCouple
public class ReactionController {

    private final ReactionService reactionService;

    @Operation(summary = "반응 토글", description = "같은 이모지가 있으면 취소, 없으면 추가. 다른 이모지는 여러 개 가능")
    @PostMapping
    public ReactionToggleResponse toggle(
        @AuthenticationPrincipal UUID userId,
        @PathVariable UUID postId,
        @Valid @RequestBody ReactionToggleRequest request
    ) {
        return reactionService.toggle(userId, postId, request.emojiType());
    }
}
