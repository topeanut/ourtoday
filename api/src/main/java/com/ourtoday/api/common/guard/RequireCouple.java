package com.ourtoday.api.common.guard;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 커플 연결(CONNECTED) 완료 유저만 호출 가능. 프로필 체크 포함
 * 프로필 미완료 시 403 PROFILE_REQUIRED, 커플 미연결 시 403 COUPLE_REQUIRED
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireCouple {
}
