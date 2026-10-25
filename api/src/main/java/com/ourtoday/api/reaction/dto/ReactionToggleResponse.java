package com.ourtoday.api.reaction.dto;

import com.ourtoday.api.reaction.EmojiType;
import io.swagger.v3.oas.annotations.media.Schema;

public record ReactionToggleResponse(
    @Schema(description = "true: 추가됨, false: 취소됨") boolean reacted,
    EmojiType emojiType
) {}
