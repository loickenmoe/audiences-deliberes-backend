package com.afriland.audiences.dossier.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Direct setting or removal of special diligence by DJ or DJA, without a proposal (they decide
 * directly).
 * <p>{@code estSensible = true} requires {@code seuilMontant} and {@code typeClientSensible}.
 * {@code false} is a removal and requires {@code motif}: the case leaves the double fee validation
 * workflow, and the audit will ask why.
 */
public record ModifierSensibiliteRequest(
        @NotNull Boolean estSensible,
        @PositiveOrZero Double seuilMontant,
        @Size(max = 50) String typeClientSensible,
        @Size(max = 255) String motif) {
}
