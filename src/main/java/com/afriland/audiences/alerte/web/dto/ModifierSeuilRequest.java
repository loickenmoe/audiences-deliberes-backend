package com.afriland.audiences.alerte.web.dto;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * {@code PUT /alertes/seuils} (FR-ALR-01, US 7.1). Only {@code type=CHARGE_MAX_AVOCAT} is
 * supported: it is the only alert type backed by a configurable threshold (RG-ALR-02).
 */
public record ModifierSeuilRequest(
        @NotNull TypeAlerte type,
        @NotNull @Positive Integer seuil) {
}
