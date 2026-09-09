package com.afriland.audiences.common.error;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
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

import java.util.stream.Collectors;

/**
 * Traduit toute exception non gérée par un contrôleur en {@link ErrorResponse}, conformément au
 * format d'erreur du contrat d'API (REF-07 §1.3-1.4) : codes HTTP standard + corps JSON
 * {@code {code, message, details}}.
 * <p>
 * Étend {@link ResponseEntityExceptionHandler} pour intercepter proprement les exceptions
 * standard de Spring MVC (validation, désérialisation, méthode HTTP non supportée, etc.), en
 * plus des {@link ApiException} métier levées explicitement par les services applicatifs.
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Erreur interne inattendue", ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse(ErrorCodes.ERREUR_INTERNE, "Erreur interne", null));
    }

    // ---- Exceptions Spring MVC standard (interceptées via ResponseEntityExceptionHandler) ----

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorCodes.REQUETE_INVALIDE, "Requête invalide", details));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorCodes.REQUETE_INVALIDE, "Corps de requête illisible ou mal formé", null));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorCodes.REQUETE_INVALIDE, "Paramètre requis manquant", ex.getParameterName()));
    }

    @Override
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
     * RG-GED-08 : intercepte le rejet Spring/Tomcat (413, format {@code ProblemDetail} générique)
     * avant même que {@code GedService} ne voie la requête, pour rester conforme au format
     * {@code {code, message, details}} du contrat — découvert en validation manuelle M8, un
     * fichier dépassant {@code spring.servlet.multipart.max-file-size} déclenche cette exception
     * en amont du contrôleur, quel que soit le seuil {@code TAILLE_MAX_GED} configuré.
     * <p>
     * Contrairement aux autres cas ci-dessus, ce n'est pas un {@code @ExceptionHandler} distinct
     * (ambigu avec le {@code handleException} générique final de {@link ResponseEntityExceptionHandler}
     * — constaté via {@code contextLoads()} en échec) mais l'override du point d'extension dédié
     * que la classe mère expose déjà pour ce type précis.
     */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorCodes.DOCUMENT_NON_CONFORME, "Fichier trop volumineux", null));
    }

    String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
