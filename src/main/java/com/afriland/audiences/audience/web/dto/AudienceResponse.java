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
        /** Hearing court, inherited from the case unless specified. */
        String juridiction,
        String compteRendu,
        StatutAudience statut,
        LocalDateTime createdAt,
        String motifAnnulation,
        LocalDateTime dateAnnulation,
        /** Note addressed to the assigned lawyer, separate from the internal report. */
        String noteAvocat) {
}
