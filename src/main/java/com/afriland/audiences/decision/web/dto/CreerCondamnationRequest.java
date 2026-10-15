package com.afriland.audiences.decision.web.dto;

import com.afriland.audiences.decision.domain.SensCondamnation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** FR-DEF-02/US 8.2 : aucune précondition d'étape n'est spécifiée (Q-59), à la différence de l'adjudication. */
public record CreerCondamnationRequest(
        /** Obligatoire depuis QF-66 : l'étape clôturée qui a rendu la décision, comme l'adjudication. */
        @NotNull Long etapeId,
        @NotNull SensCondamnation sens,
        @NotBlank String naturePaiement,
        @NotNull LocalDate dateDecision) {
}
