package com.afriland.audiences.audience.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * A hearing calendar row (FR-AUD-05): court, step, in-house lawyer(s), lawyer(s), originating unit,
 * client account, subject (case reference), risk, next date (= this hearing's
 * {@code datePlanifiee}).
 */
public record CalendrierEntreeResponse(
        Long audienceId,
        Long dossierId,
        String referenceDossier,
        String juridiction,
        String etape,
        List<Long> juristesAffectes,
        List<Long> avocatsAffectes,
        String codeAgenceOrigine,
        String clientNom,
        Double risqueEncouru,
        LocalDate datePlanifiee) {
}
