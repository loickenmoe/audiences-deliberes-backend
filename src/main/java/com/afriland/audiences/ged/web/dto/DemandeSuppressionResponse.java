package com.afriland.audiences.ged.web.dto;

import com.afriland.audiences.ged.domain.StatutSuppression;

import java.time.LocalDateTime;

/**
 * The request names its target (file, type, case file): otherwise the DJ/DJA queue would only show
 * a document id, and an approved request's history would be anonymous (the deleted document
 * returns 404).
 */
public record DemandeSuppressionResponse(
        Long id,
        Long documentId,
        String documentNom,
        String typeDocument,
        Long dossierId,
        String referenceDossier,
        Long demandeurId,
        String motif,
        StatutSuppression statut,
        Long valideParId,
        String motifRejet,
        LocalDateTime dateDemande,
        LocalDateTime dateDecision,
        LocalDateTime dateDerniereRelance) {
}
