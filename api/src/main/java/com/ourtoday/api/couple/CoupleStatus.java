package com.ourtoday.api.couple;

public enum CoupleStatus {
    /** 초대코드 생성, 상대 합류 대기 */
    PENDING,
    /** 두 명 연결 완료 */
    CONNECTED,
    /** 해제됨 (멤버 연결 없음, 기록만 보관) */
    BROKEN
}
