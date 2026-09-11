package com.afriland.audiences.frais.web.dto;

import com.afriland.audiences.frais.domain.DecisionValidationFrais;
import com.afriland.audiences.frais.domain.EtapeValidationFrais;

import java.time.LocalDateTime;

/**
 * Une décision du circuit (Q-05, Q-82) : étape, décision, motif, auteur, date — ce qui permet au
 * DJ de voir l'accord déjà donné par la DJA (US 4.5) et à chacun de savoir s'il a déjà voté.
 */
public record ValidationFraisResponse(
        EtapeValidationFrais etape,
        DecisionValidationFrais decision,
        String motif,
        Long utilisateurId,
        String utilisateurNom,
        LocalDateTime dateValidation) {
}
