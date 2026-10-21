package com.afriland.audiences.common.error;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.sql.SQLException;
import java.util.stream.Collectors;

/**
 * Translates unhandled controller exceptions into an {@link ErrorResponse} per the API contract
 * (REF-07 §1.3-1.4): standard HTTP status plus {@code {code, message, details}}. Extends
 * {@link ResponseEntityExceptionHandler} to also cover standard Spring MVC exceptions.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        log.warn("ApiException [{}] {} - {}", ex.getCode(), ex.getStatus(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(new ErrorResponse(ex.getCode(), ex.getMessage(), ex.getDetails(), ex.getChamp()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorCodes.REQUETE_INVALIDE, "Requête invalide", details));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(ErrorCodes.DROITS_INSUFFISANTS, "Droits insuffisants", null));
    }

    /**
     * Two concurrent creations can pass the same service uniqueness check; the database constraint
     * rejects the second. A unique violation (SQLState {@code 23505}) is therefore a 409 conflict;
     * any other integrity violation stays an internal error.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        if (!estViolationUnicite(ex)) {
            return handleUnexpected(ex);
        }
        log.warn("Violation d'unicité rendue en 409 : {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(ErrorCodes.CONFLIT,
                        "Cette valeur est déjà utilisée : une autre requête vient de l'enregistrer", null));
    }

    static boolean estViolationUnicite(Throwable ex) {
        for (Throwable cause = ex; cause != null && cause.getCause() != cause; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Erreur interne inattendue", ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse(ErrorCodes.ERREUR_INTERNE, "Erreur interne", null));
    }

    // Standard Spring MVC exceptions (via ResponseEntityExceptionHandler)

    // Signature imposed by ResponseEntityExceptionHandler: java:S2638 flags its generic type argument (false positive).
    @Override
    @SuppressWarnings("java:S2638")
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorCodes.REQUETE_INVALIDE, "Requête invalide", details));
    }

    // Signature imposed by ResponseEntityExceptionHandler: java:S2638 flags its generic type argument (false positive).
    @Override
    @SuppressWarnings("java:S2638")
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorCodes.REQUETE_INVALIDE, "Corps de requête illisible ou mal formé", null));
    }

    // Signature imposed by ResponseEntityExceptionHandler: java:S2638 flags its generic type argument (false positive).
    @Override
    @SuppressWarnings("java:S2638")
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorCodes.REQUETE_INVALIDE, "Paramètre requis manquant", ex.getParameterName()));
    }

    // Signature imposed by ResponseEntityExceptionHandler: java:S2638 flags its generic type argument (false positive).
    @Override
    @SuppressWarnings("java:S2638")
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse(ErrorCodes.REQUETE_INVALIDE, "Méthode HTTP non supportée pour cette ressource", null));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorCodes.REQUETE_INVALIDE,
                        "Paramètre invalide : " + ex.getName(), String.valueOf(ex.getValue())));
    }

    /**
     * RG-GED-08: a file exceeding {@code spring.servlet.multipart.max-file-size} is rejected before
     * reaching the controller, whatever {@code TAILLE_MAX_GED} is; map it to the contract error format.
     * <p>
     * Implemented as an override of the parent's dedicated hook: a separate {@code @ExceptionHandler}
     * would be ambiguous with {@link ResponseEntityExceptionHandler}'s generic handler.
     */
    // Signature imposed by ResponseEntityExceptionHandler: java:S2638 flags its generic type argument (false positive).
    @Override
    @SuppressWarnings("java:S2638")
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorCodes.DOCUMENT_NON_CONFORME, "Fichier trop volumineux", null));
    }

    String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
