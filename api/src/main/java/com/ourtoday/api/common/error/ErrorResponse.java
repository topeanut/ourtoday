package com.ourtoday.api.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * { "error": { "code", "message", "timestamp", "details"? } }
 */
public record ErrorResponse(Body error) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(
        String code,
        String message,
        Instant timestamp,
        Map<String, String> details
    ) {}

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(new Body(code.name(), message, Instant.now(), null));
    }

    public static ErrorResponse of(ErrorCode code) {
        return of(code, code.getDefaultMessage());
    }

    public static ErrorResponse withDetails(ErrorCode code, Map<String, String> details) {
        return new ErrorResponse(new Body(code.name(), code.getDefaultMessage(), Instant.now(), details));
    }
}
