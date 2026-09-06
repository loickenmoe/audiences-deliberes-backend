package com.afriland.audiences.intervenant.web.dto;

import com.afriland.audiences.intervenant.domain.StatutConstitution;
import jakarta.validation.constraints.NotNull;

/** {@code decision} ∈ {VALIDEE, REJETEE} ; {@code motifRejet} requis si REJETEE (RG-INT-08). */
public record ValiderConstitutionRequest(@NotNull StatutConstitution decision, String motifRejet) {
}
