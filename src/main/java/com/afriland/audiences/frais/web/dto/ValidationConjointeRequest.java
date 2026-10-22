package com.afriland.audiences.frais.web.dto;

import jakarta.validation.constraints.NotNull;

/** FR-INT-04: {@code motifRejet} required when {@code accord = false} (RG-INT-03, same rule as FR-INT-03). */
public record ValidationConjointeRequest(@NotNull Boolean accord, String motifRejet) {
}
