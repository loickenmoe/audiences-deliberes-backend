package com.afriland.audiences.frais.web.dto;

import jakarta.validation.constraints.NotNull;

/** FR-INT-04 : {@code motifRejet} obligatoire si {@code accord = false} (RG-INT-03, même règle que FR-INT-03). */
public record ValidationConjointeRequest(@NotNull Boolean accord, String motifRejet) {
}
