package com.afriland.audiences.delibere.web.dto;

import com.afriland.audiences.delibere.domain.ResultatDelibere;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Q-78 : vider un délibéré en attente — le tribunal a rendu sa décision. Mêmes règles d'échéance
 * de recours qu'à l'enregistrement direct (RG-DEL-01, Q-42) : requise et postérieure à la date du
 * délibéré pour {@code DEFAVORABLE}/{@code MIXTE}, absente pour {@code FAVORABLE}.
 */
public record ViderDelibereRequest(
        @NotNull ResultatDelibere resultat,
        LocalDateTime dateEcheanceRecours) {
}
