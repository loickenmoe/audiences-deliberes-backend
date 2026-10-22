package com.afriland.audiences.dossier.web.dto;

import com.afriland.audiences.dossier.domain.StatutValidation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code motif} is mandatory when {@code statut == REJETEE} (checked in the service). */
public record ValiderDerogationRequest(@NotNull StatutValidation statut, @Size(max = 255) String motif) {
}
