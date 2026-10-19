package com.afriland.audiences.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Error response body per the API contract (REF-07 §1.3): {@code {code, message, details}};
 * {@code details} is optional.
 * <p>
 * {@code champ} names, when possible, the request field to fix ({@code details} is human-readable
 * text, not a machine identifier). It is omitted from the JSON when {@code null}, keeping the change
 * backward compatible.
 */
public record ErrorResponse(
        String code,
        String message,
        String details,
        @JsonInclude(JsonInclude.Include.NON_NULL) String champ) {

    public ErrorResponse(String code, String message) {
        this(code, message, null, null);
    }

    public ErrorResponse(String code, String message, String details) {
        this(code, message, details, null);
    }
}
