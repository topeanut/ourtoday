package com.ourtoday.api.push;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 푸시 대상 플랫폼. JSON 값은 소문자 (android / ios)
 */
public enum PushPlatform {
    ANDROID("android"),
    IOS("ios");

    private final String value;

    PushPlatform(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static PushPlatform from(String value) {
        for (PushPlatform p : values()) {
            if (p.value.equals(value)) return p;
        }
        throw new IllegalArgumentException("unknown platform: " + value);
    }
}
