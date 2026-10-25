package com.ourtoday.api.reaction;

import java.util.UUID;

/**
 * 집계 쿼리 결과 (JPQL constructor expression)
 */
public record ReactionCount(UUID postId, EmojiType emojiType, long count, long mine) {

    public boolean reactedByMe() {
        return mine > 0;
    }
}
