package com.afriland.audiences.reporting.web.dto;

import java.time.LocalDate;

/**
 * One row behind a dashboard block. A single row shape is used for all indicators, although the
 * counted objects differ in nature, so that the screen shows one consistent table.
 *
 * @param id               id of the counted object, giving the row a stable key
 * @param dossierId        {@code null} for an object outside any case; the row is still shown,
 *                         unlinked, so that the row count matches the displayed number
 * @param referenceDossier readable case reference; {@code null} if the case has since been deleted
 * @param intitule         row label: client for a case, court for a hearing, invoice for fees
 * @param precision        secondary information (current step, status, lawyer), or {@code null}
 * @param date             date locating the row, or {@code null} when the object has none
 * @param montant          set for fees and provisions only, {@code null} elsewhere
 */
public record DetailIndicateurResponse(
        Long id,
        Long dossierId,
        String referenceDossier,
        String intitule,
        String precision,
        LocalDate date,
        Double montant) {
}
