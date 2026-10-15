package com.afriland.audiences.intervenant.web.dto;

import com.afriland.audiences.intervenant.domain.StatutConstitution;
import com.afriland.audiences.intervenant.domain.TypeIntervenant;

import java.time.LocalDateTime;

/**
 * Depuis Q-86 : dossier, prestataire (et son type), demandeur et signataire nommés.
 *
 * @param redactionPossible vrai si <b>celui qui lit</b> peut écrire sur la lettre : son demandeur,
 *                          ou le supérieur hiérarchique (Q-126). L'écran ne saurait pas le calculer
 *                          - il connaît les rôles de qui regarde, pas l'identifiant interne du
 *                          demandeur - et lui confier la règle reviendrait à la dédoubler.
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
     * La même réponse, avec le droit d'écrire renseigné.
     *
     * <p>Le mappeur ne peut pas le calculer : il ne connaît pas la session. Il laisse donc le champ
     * à faux, et le service - qui sait qui demande - pose la vraie valeur ici. La liste des champs
     * ne vit qu'à un seul endroit, celui-ci.
     */
    public DemandeConstitutionResponse avecRedactionPossible(boolean possible) {
        return new DemandeConstitutionResponse(id, dossierId, prestataireId, demandeurId, motif,
                statut, valideParId, motifRejet, lettreDocumentId, dateDemande, dateDecision,
                referenceDossier, prestataireNom, prestataireType, demandeurNom, valideParNom,
                possible);
    }
}
