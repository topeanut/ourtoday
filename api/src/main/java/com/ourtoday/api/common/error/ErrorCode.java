package com.ourtoday.api.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API.md "표준 에러 코드" 와 1:1 대응. 새 코드는 API.md 먼저 갱신
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    PROFILE_REQUIRED(HttpStatus.FORBIDDEN, "프로필 입력이 필요한 기능입니다."),
    COUPLE_REQUIRED(HttpStatus.FORBIDDEN, "커플 연결이 필요한 기능입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVITE_CODE_INVALID(HttpStatus.BAD_REQUEST, "유효하지 않은 초대코드입니다."),
    INVITE_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "만료된 초대코드입니다."),
    ALREADY_IN_COUPLE(HttpStatus.CONFLICT, "이미 연결된 커플이 있습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;
}
