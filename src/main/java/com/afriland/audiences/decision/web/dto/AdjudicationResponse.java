package com.afriland.audiences.decision.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdjudicationResponse(
        Long id,
        Long dossierId,
        Long etapeId,
        String beneficiaire,
        String statutComptabilisation,
        Boolean repriseProvision,
        Boolean restitutionSoulte,
        Boolean mutation,
        Double reliquat,
        LocalDate dateDecision,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
