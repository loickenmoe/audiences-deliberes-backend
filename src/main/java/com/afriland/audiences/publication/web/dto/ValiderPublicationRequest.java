package com.afriland.audiences.publication.web.dto;

import com.afriland.audiences.publication.domain.StatutPublication;
import jakarta.validation.constraints.NotNull;

/** {@code motifRejet} required if {@code statut = REJETE} (RG-INT-10). */
public record ValiderPublicationRequest(@NotNull StatutPublication statut, String motifRejet) {
}
