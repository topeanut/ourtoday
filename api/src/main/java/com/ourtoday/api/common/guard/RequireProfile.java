package com.ourtoday.api.common.guard;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 프로필(닉네임) 입력 완료 유저만 호출 가능. 미완료 시 403 PROFILE_REQUIRED
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireProfile {
}
