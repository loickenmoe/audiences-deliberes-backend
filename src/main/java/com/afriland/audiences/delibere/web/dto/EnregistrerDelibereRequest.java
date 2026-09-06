package com.afriland.audiences.delibere.web.dto;

import com.afriland.audiences.delibere.domain.ResultatDelibere;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * {@code dateEcheanceRecours} requise et postérieure à {@code dateDeliberee} si {@code resultat}
 * ∈ {DEFAVORABLE, MIXTE} (voie de recours ouverte, Q-42) ; doit rester absente sinon (RG-DEL-01).
 */
public record EnregistrerDelibereRequest(
        @NotNull Long etapeId,
        @NotNull LocalDate dateDeliberee,
        @NotNull ResultatDelibere resultat,
        LocalDateTime dateEcheanceRecours) {
}
