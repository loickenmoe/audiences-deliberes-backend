package com.afriland.audiences.ged.web.dto;

import com.afriland.audiences.ged.domain.StatutSuppression;
import jakarta.validation.constraints.NotNull;

/** {@code decision} ∈ {APPROUVEE, REJETEE} ; {@code motifRejet} requis si REJETEE (RG-GED-09). */
public record ApprobationSuppressionRequest(@NotNull StatutSuppression decision, String motifRejet) {
}
