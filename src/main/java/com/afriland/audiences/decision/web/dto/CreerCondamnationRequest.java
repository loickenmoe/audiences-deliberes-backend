package com.afriland.audiences.decision.web.dto;

import com.afriland.audiences.decision.domain.SensCondamnation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** FR-DEF-02/US 8.2: no step precondition, unlike the adjudication. */
public record CreerCondamnationRequest(
        /** Required: the closed step that issued the decision, as for the adjudication. */
        @NotNull Long etapeId,
        @NotNull SensCondamnation sens,
        @NotBlank String naturePaiement,
        @NotNull LocalDate dateDecision) {
}
