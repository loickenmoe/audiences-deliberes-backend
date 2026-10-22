package com.afriland.audiences.delibere.web.dto;

import com.afriland.audiences.delibere.domain.ResultatDelibere;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Without {@code resultat}: placed under deliberation; {@code dateDeliberee} is the date announced
 * by the court, the step stays {@code EN_DELIBERE} and no appeal deadline is allowed. With
 * {@code resultat}: an already-rendered deliberation, recorded and delivered in one go;
 * {@code dateEcheanceRecours} is required and later than {@code dateDeliberee} when {@code resultat}
 * is DEFAVORABLE or MIXTE (appeal open), absent otherwise (RG-DEL-01).
 */
public record EnregistrerDelibereRequest(
        @NotNull Long etapeId,
        @NotNull LocalDate dateDeliberee,
        ResultatDelibere resultat,
        LocalDateTime dateEcheanceRecours) {
}
