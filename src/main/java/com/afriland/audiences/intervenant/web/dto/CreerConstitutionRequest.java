package com.afriland.audiences.intervenant.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreerConstitutionRequest(@NotNull Long dossierId, @NotNull Long prestataireId, @NotBlank String motif) {
}
