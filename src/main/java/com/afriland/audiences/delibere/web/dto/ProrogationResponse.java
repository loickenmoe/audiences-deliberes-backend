package com.afriland.audiences.delibere.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * {@code alerte} (Q-17) : {@code true} dès que le compteur de prorogations dépasse le seuil
 * configuré ({@code SEUIL_PROROGATIONS}, Q-18) — pas seulement lors de la prorogation qui
 * franchit ce seuil. Ne s'agit pas d'une erreur HTTP : la prorogation est toujours enregistrée
 * avec le code {@code 201 Created}.
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
