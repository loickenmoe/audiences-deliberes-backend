package com.afriland.audiences.reporting.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * A past hearing whose legal officer report has not been entered.
 *
 * <p>{@code TENUE} is only set when the report is entered, so a past hearing still
 * {@code PLANIFIEE} is unambiguously missing its report. Today's hearings are never included: the
 * upper bound stops at yesterday whatever the requested window.
 *
 * @param joursDeRetard           days elapsed since the hearing date (age of the gap, not a
 *                                regulatory deadline)
 * @param compteRenduAvocatDepose whether the lawyer has already filed a report, which distinguishes
 *                                "nothing reported" from "the bank has not yet written it up"
 */
public record CompteRenduAttenduResponse(
        Long audienceId,
        Long dossierId,
        String referenceDossier,
        String juridiction,
        String etape,
        LocalDate datePlanifiee,
        long joursDeRetard,
        List<Long> juristesAffectes,
        boolean compteRenduAvocatDepose) {
}
