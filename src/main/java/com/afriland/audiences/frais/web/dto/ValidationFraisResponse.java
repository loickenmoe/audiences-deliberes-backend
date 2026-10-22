package com.afriland.audiences.frais.web.dto;

import com.afriland.audiences.frais.domain.DecisionValidationFrais;
import com.afriland.audiences.frais.domain.EtapeValidationFrais;

import java.time.LocalDateTime;

/**
 * One workflow decision: step, decision, reason, author, date. Lets the DJ see the approval already
 * given by the DJA (US 4.5) and everyone know whether they have already voted.
 */
public record ValidationFraisResponse(
        EtapeValidationFrais etape,
        DecisionValidationFrais decision,
        String motif,
        Long utilisateurId,
        String utilisateurNom,
        LocalDateTime dateValidation) {
}
