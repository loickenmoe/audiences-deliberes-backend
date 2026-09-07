package com.afriland.audiences.decision.web.dto;

import com.afriland.audiences.decision.domain.StatutPaiementCondamnation;
import jakarta.validation.constraints.NotNull;

public record ModifierStatutCondamnationRequest(@NotNull StatutPaiementCondamnation statutPaiement) {
}
