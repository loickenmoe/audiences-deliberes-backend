package com.afriland.audiences.audience.web.dto;

import com.afriland.audiences.audience.domain.StatutAudience;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AudienceResponse(
        Long id,
        Long dossierId,
        Long etapeId,
        LocalDate datePlanifiee,
        LocalDate dateTenue,
        /** Juridiction de l'audience, héritée du dossier faute d'indication contraire. */
        String juridiction,
        String compteRendu,
        StatutAudience statut,
        LocalDateTime createdAt,
        String motifAnnulation,
        LocalDateTime dateAnnulation) {
}
