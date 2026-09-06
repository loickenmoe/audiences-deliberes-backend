package com.afriland.audiences.ged.web.dto;

import com.afriland.audiences.ged.domain.StatutSuppression;

import java.time.LocalDateTime;

public record DemandeSuppressionResponse(
        Long id,
        Long documentId,
        Long demandeurId,
        String motif,
        StatutSuppression statut,
        Long valideParId,
        String motifRejet,
        LocalDateTime dateDemande,
        LocalDateTime dateDecision,
        LocalDateTime dateDerniereRelance) {
}
