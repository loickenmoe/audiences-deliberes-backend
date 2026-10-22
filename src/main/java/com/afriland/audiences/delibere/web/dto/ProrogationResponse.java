package com.afriland.audiences.delibere.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * {@code alerte} is {@code true} as soon as the extension count exceeds the configured threshold
 * ({@code SEUIL_PROROGATIONS}), not only on the extension that crosses it. It is not an HTTP error:
 * the extension is always recorded with {@code 201 Created}.
 */
public record ProrogationResponse(
        Long id,
        Long delibereId,
        LocalDate date,
        String motif,
        int compteur,
        boolean alerte,
        LocalDateTime createdAt) {
}
