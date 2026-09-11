package com.afriland.audiences.frais.web.dto;

import com.afriland.audiences.frais.domain.StatutCircuitFrais;
import com.afriland.audiences.frais.domain.StatutPaiementFrais;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Depuis Q-82 : la demande se lit sans autre appel — dossier et avocat nommés, sensibilité et seuil
 * applicable qui expliquent {@code validationConjointeRequise} (RG-INT-03), et décisions du circuit
 * dans l'ordre chronologique.
 */
public record DemandeFraisResponse(
        Long id,
        Long dossierId,
        String referenceDossier,
        Long avocatId,
        String avocatNom,
        Double montant,
        List<String> piecesJustificatives,
        StatutCircuitFrais statutCircuit,
        String motifRejet,
        StatutPaiementFrais statutPaiement,
        String referenceFacture,
        Boolean dossierSensible,
        Double seuilApplicable,
        boolean validationConjointeRequise,
        List<ValidationFraisResponse> validations,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
