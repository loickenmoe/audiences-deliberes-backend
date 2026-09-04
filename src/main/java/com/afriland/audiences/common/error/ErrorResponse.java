package com.afriland.audiences.common.error;

/**
 * Corps de réponse d'erreur, conforme au contrat d'API (REF-07 §1.3) :
 * {@code {code, message, details}}. {@code details} est optionnel (peut être {@code null}).
 */
public record ErrorResponse(String code, String message, String details) {

    public ErrorResponse(String code, String message) {
        this(code, message, null);
    }
}
