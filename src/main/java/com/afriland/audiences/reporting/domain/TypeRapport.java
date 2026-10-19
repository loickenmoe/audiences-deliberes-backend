package com.afriland.audiences.reporting.domain;

/**
 * Granularity of a periodic report (FR-REP-01, US 8.4). The caller supplies {@code dateDebut} and
 * {@code dateFin} explicitly (unlike {@code PeriodeCalendrier}), so this type is only a period
 * label with no effect on computation.
 */
public enum TypeRapport {
    JOURNALIER,
    HEBDOMADAIRE,
    MENSUEL,
    TRIMESTRIEL,
    ANNUEL
}
