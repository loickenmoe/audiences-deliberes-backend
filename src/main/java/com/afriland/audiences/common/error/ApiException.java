package com.afriland.audiences.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Exception métier applicative, porteuse d'un statut HTTP et d'un code d'erreur applicatif
 * (cf. {@link ErrorCodes}). Capturée par {@link GlobalExceptionHandler} et traduite en
 * {@link ErrorResponse} conforme au contrat d'API.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String details;

    /**
     * Champ de la requête à corriger, ou {@code null} quand l'erreur n'en vise aucun. Repris tel
     * quel dans {@link ErrorResponse#champ()} : c'est ce qui permet à un client de signaler
     * l'erreur sur le bon champ sans la déduire de l'endpoint appelé.
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
     * Conflit imputable à un champ précis de la requête : doublon de référence, de compte, de
     * facture. À préférer à {@link #conflict(String, String)} chaque fois que le champ fautif est
     * connu — le client peut alors surligner ce champ au lieu d'afficher un bandeau générique.
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
