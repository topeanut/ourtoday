package com.ourtoday.api.push.event;

import java.util.UUID;

public record CoupleConnectedEvent(UUID coupleId, UUID joinerId) {
}
