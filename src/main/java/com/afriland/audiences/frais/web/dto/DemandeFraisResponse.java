package com.afriland.audiences.frais.web.dto;

import com.afriland.audiences.frais.domain.StatutCircuitFrais;
import com.afriland.audiences.frais.domain.StatutPaiementFrais;

import java.time.LocalDateTime;
import java.util.List;

public record DemandeFraisResponse(
        Long id,
        Long dossierId,
        Long avocatId,
        Double montant,
        List<String> piecesJustificatives,
        StatutCircuitFrais statutCircuit,
        String motifRejet,
        StatutPaiementFrais statutPaiement,
        String referenceFacture,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
