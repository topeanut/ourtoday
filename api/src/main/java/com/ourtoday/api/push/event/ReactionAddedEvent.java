package com.ourtoday.api.push.event;

import java.util.UUID;

public record ReactionAddedEvent(UUID postId, UUID reactorId) {
}
