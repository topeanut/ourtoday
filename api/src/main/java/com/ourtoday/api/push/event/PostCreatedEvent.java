package com.ourtoday.api.push.event;

import java.time.LocalDate;
import java.util.UUID;

public record PostCreatedEvent(UUID postId, UUID authorId, UUID coupleId, LocalDate postedDate) {
}
