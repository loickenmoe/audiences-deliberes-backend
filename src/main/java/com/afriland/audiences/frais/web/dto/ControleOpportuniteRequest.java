package com.afriland.audiences.frais.web.dto;

import jakarta.validation.constraints.NotNull;

/** FR-INT-03 : {@code motifRejet} obligatoire si {@code accord = false} (RG-INT-03). */
public record ControleOpportuniteRequest(@NotNull Boolean accord, String motifRejet) {
}
