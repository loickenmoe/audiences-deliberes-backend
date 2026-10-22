package com.afriland.audiences.reporting.web.dto;

import com.afriland.audiences.common.audit.TypeActionHistorique;

import java.time.LocalDateTime;

/**
 * One action behind a count of the activity report, with enough to open what it touched. Relies on
 * the history's {@code dossier_id} column rather than the action-dependent {@code jsonb} details.
 *
 * @param dossierId        {@code null} for an action outside any case (configuration, client
 *                         reference data). The row is still shown, unlinked, so that the count
 *                         matches the number of rows.
 * @param referenceDossier readable case reference; {@code null} if the case has since been deleted
 */
public record ActionDetailleeResponse(
        Long id,
        TypeActionHistorique code,
        String action,
        LocalDateTime dateAction,
        Long dossierId,
        String referenceDossier) {
}
