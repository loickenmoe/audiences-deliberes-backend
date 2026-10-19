package com.afriland.audiences.frais.web.dto;

import com.afriland.audiences.frais.domain.StatutCircuitFrais;
import com.afriland.audiences.frais.domain.StatutPaiementFrais;

import java.time.LocalDateTime;
import com.afriland.audiences.portail.web.dto.PieceJustificativeResponse;

import java.util.List;

/**
 * The request is readable without further calls: case and lawyer named, sensitivity and applicable
 * threshold explaining {@code validationConjointeRequise} (RG-INT-03), and workflow decisions in
 * chronological order.
 * <p>
 * {@code piecesTelechargeables} holds the actually stored documents with a pre-signed URL
 * (10 minutes), as UC-INT-02 step 3 requires; filled on the <b>detail</b> view only, empty on the
 * list. {@code piecesJustificatives} is the raw array, which may contain entries predating upload
 * that point to no file.
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
        List<PieceJustificativeResponse> piecesTelechargeables,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
