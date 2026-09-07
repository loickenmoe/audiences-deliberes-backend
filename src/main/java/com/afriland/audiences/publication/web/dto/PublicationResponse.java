package com.afriland.audiences.publication.web.dto;

import com.afriland.audiences.publication.domain.StatutPublication;
import com.afriland.audiences.publication.domain.TypeAutrePublication;
import com.afriland.audiences.publication.domain.TypePublication;

import java.time.LocalDateTime;

/**
 * Représentation aplatie de {@link com.afriland.audiences.publication.domain.Publication} — les
 * champs propres à un sous-type ({@code audienceId}/{@code contenu} pour un compte rendu,
 * {@code typeDocument} pour une autre publication) sont {@code null} pour l'autre sous-type.
 */
public record PublicationResponse(
        Long id,
        TypePublication type,
        Long dossierId,
        Long avocatId,
        LocalDateTime dateDepot,
        StatutPublication statutValidation,
        String motifRejet,
        Long restrictionAccesId,
        Long audienceId,
        String contenu,
        TypeAutrePublication typeDocument) {
}
