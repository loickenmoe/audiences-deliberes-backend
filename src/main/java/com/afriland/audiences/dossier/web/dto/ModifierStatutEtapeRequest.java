package com.afriland.audiences.dossier.web.dto;

import com.afriland.audiences.dossier.domain.StatutCycleVie;
import jakarta.validation.constraints.NotNull;

public record ModifierStatutEtapeRequest(@NotNull StatutCycleVie nouveauStatut) {
}
