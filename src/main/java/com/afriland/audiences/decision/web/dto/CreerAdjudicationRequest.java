package com.afriland.audiences.decision.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

/** FR-DEF-01/US 8.1 : étape {@code CLOTURE} requise (RG-DEF-01). */
public record CreerAdjudicationRequest(
        @NotNull Long etapeId,
        @NotBlank String beneficiaire,
        @NotBlank String statutComptabilisation,
        @NotNull Boolean repriseProvision,
        @NotNull Boolean restitutionSoulte,
        @NotNull Boolean mutation,
        @NotNull @PositiveOrZero Double reliquat,
        @NotNull LocalDate dateDecision) {
}
