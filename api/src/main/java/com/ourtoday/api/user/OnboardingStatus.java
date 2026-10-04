package com.ourtoday.api.user;

public enum OnboardingStatus {
    /** 카카오 로그인만 됨, 닉네임 입력 필요 */
    NEED_PROFILE,
    /** 프로필 완료, 커플 없음 → 초대코드 화면 */
    NO_COUPLE,
    /** 내가 초대코드를 만들고 대기 중 */
    PENDING_INVITE,
    /** 커플 연결 완료 → 홈 */
    CONNECTED
}
