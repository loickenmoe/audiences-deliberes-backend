package com.afriland.audiences.intervenant.web.dto;

import com.afriland.audiences.intervenant.domain.StatutConstitution;

import java.time.LocalDateTime;

public record DemandeConstitutionResponse(
        Long id,
        Long dossierId,
        Long prestataireId,
        Long demandeurId,
        String motif,
        StatutConstitution statut,
        Long valideParId,
        String motifRejet,
        Long lettreDocumentId,
        LocalDateTime dateDemande,
        LocalDateTime dateDecision) {
}
