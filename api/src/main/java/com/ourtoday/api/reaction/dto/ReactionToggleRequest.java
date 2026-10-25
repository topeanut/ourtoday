package com.ourtoday.api.reaction.dto;

import com.ourtoday.api.reaction.EmojiType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ReactionToggleRequest(
    @Schema(description = "HEART, LAUGH, SURPRISE, SAD, FIRE", example = "HEART")
    @NotNull EmojiType emojiType
) {}
