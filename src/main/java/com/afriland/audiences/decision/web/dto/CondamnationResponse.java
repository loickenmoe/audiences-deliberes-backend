package com.afriland.audiences.decision.web.dto;

import com.afriland.audiences.decision.domain.SensCondamnation;
import com.afriland.audiences.decision.domain.StatutPaiementCondamnation;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CondamnationResponse(
        Long id,
        Long dossierId,
        Long etapeId,
        SensCondamnation sens,
        String naturePaiement,
        StatutPaiementCondamnation statutPaiement,
        LocalDate dateDecision,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
