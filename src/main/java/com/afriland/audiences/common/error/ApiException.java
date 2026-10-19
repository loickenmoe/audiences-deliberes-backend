package com.afriland.audiences.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Application business exception carrying an HTTP status and an error code ({@link ErrorCodes}).
 * Translated into an {@link ErrorResponse} by {@link GlobalExceptionHandler}.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String details;

    /**
     * Request field to fix, or {@code null}. Exposed as {@link ErrorResponse#champ()} so clients can
     * flag the right field without inferring it from the endpoint.
     */
    private final String champ;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, String details) {
        this(status, code, message, details, null);
    }

    public ApiException(HttpStatus status, String code, String message, String details, String champ) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
        this.champ = champ;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    /**
     * Error tied to a specific request field (duplicate reference, account, invoice). Prefer the
     * field-specific variants whenever the field is known, so the client can highlight it.
     */
    public static ApiException badRequestSurChamp(String code, String message, String champ) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, null, champ);
    }

    public static ApiException conflictSurChamp(String code, String message, String champ) {
        return new ApiException(HttpStatus.CONFLICT, code, message, null, champ);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    public static ApiException unprocessable(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
