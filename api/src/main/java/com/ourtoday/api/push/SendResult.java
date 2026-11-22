package com.ourtoday.api.push;

public enum SendResult {
    OK,
    /** 토큰이 더 이상 유효하지 않음 (앱 삭제 등) → 저장된 토큰 삭제 대상 */
    INVALID_TOKEN,
    FAILED
}
