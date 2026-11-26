package com.ourtoday.api.couple;

import java.util.UUID;

/** 커플 멤버십이 지워지기 직전(같은 트랜잭션, 동기)에 발행. 이 시점엔 아직 상대를 조회할 수 있음 */
public record CoupleUnlinkingEvent(UUID coupleId, UUID userId) {
}
