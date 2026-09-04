package com.afriland.audiences.alerte.web.dto;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * {@code PUT /alertes/seuils} (FR-ALR-01, US 7.1). Seul {@code type=CHARGE_MAX_AVOCAT} est
 * supporté en M5 — c'est le seul type d'alerte du contrat adossé à un seuil configurable
 * (RG-ALR-02) ; les autres types n'ont pas de seuil au sens de cet endpoint.
 */
public record ModifierSeuilRequest(
        @NotNull TypeAlerte type,
        @NotNull @Positive Integer seuil) {
}
