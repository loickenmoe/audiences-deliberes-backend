package com.afriland.audiences.frais.web.dto;

import jakarta.validation.constraints.NotNull;

/** FR-INT-02: {@code motifRejet} required when {@code conforme = false} (RG-INT-03). */
public record ControleConformiteRequest(@NotNull Boolean conforme, String motifRejet) {
}
