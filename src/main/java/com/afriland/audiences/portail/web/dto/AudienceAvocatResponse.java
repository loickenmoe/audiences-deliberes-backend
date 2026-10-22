package com.afriland.audiences.portail.web.dto;

import com.afriland.audiences.audience.domain.StatutAudience;

import java.time.LocalDate;

/**
 * A hearing as seen by the case-file lawyer: enough to select it in
 * {@code POST /publications/cr-audience} (US 5.1). The internal report is not exposed: the lawyer
 * files their own.
 */
public record AudienceAvocatResponse(
        Long id,
        LocalDate datePlanifiee,
        LocalDate dateTenue,
        /** Where the hearing takes place, which the lawyer needs beyond the court seized. */
        String juridiction,
        StatutAudience statut,
        /**
         * The legal officer's note to the lawyer about this hearing. Null until the lawyer is engaged:
         * the mandate, not the assignment, opens communication.
         */
        String noteAvocat) {
}
