package com.afriland.audiences.intervenant.web.dto;

import com.afriland.audiences.intervenant.domain.StatutConstitution;
import com.afriland.audiences.intervenant.domain.TypeIntervenant;

import java.time.LocalDateTime;

/** Depuis Q-86 : dossier, prestataire (et son type), demandeur et signataire nommés. */
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
        LocalDateTime dateDecision,
        String referenceDossier,
        String prestataireNom,
        TypeIntervenant prestataireType,
        String demandeurNom,
        String valideParNom) {
}
