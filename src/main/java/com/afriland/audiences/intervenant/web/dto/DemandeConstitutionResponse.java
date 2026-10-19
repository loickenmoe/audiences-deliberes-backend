package com.afriland.audiences.intervenant.web.dto;

import com.afriland.audiences.intervenant.domain.StatutConstitution;
import com.afriland.audiences.intervenant.domain.TypeIntervenant;

import java.time.LocalDateTime;

/**
 * Appointment request with the case, provider (and its type), requester and signatory named.
 *
 * @param redactionPossible true if the current reader may write the letter (its requester or the
 *                          line manager). Computed server-side since the UI does not know the
 *                          requester's internal id.
 */
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
        String valideParNom,
        boolean redactionPossible) {

    /**
     * Same response with the write permission set. The mapper has no session, so it leaves the
     * field false and the service sets the real value here.
     */
    public DemandeConstitutionResponse avecRedactionPossible(boolean possible) {
        return new DemandeConstitutionResponse(id, dossierId, prestataireId, demandeurId, motif,
                statut, valideParId, motifRejet, lettreDocumentId, dateDemande, dateDecision,
                referenceDossier, prestataireNom, prestataireType, demandeurNom, valideParNom,
                possible);
    }
}
