package com.afriland.audiences.delibere.web.dto;

import com.afriland.audiences.delibere.domain.ResultatDelibere;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Delivers a pending deliberation once the court has ruled. Same appeal-deadline rules as direct
 * recording (RG-DEL-01): required and later than the deliberation date for
 * {@code DEFAVORABLE}/{@code MIXTE}, absent for {@code FAVORABLE}.
 */
public record ViderDelibereRequest(
        @NotNull ResultatDelibere resultat,
        LocalDateTime dateEcheanceRecours) {
}
