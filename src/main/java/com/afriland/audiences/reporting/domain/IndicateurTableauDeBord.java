package com.afriland.audiences.reporting.domain;

/**
 * Dashboard blocks that can be opened to show their detail. One indicator per displayed block.
 *
 * <p>Invariant: for each value, the detail returns exactly what the counter counts (same predicate,
 * same source). Each service method mirrors its counting query, and a test checks they agree.
 */
public enum IndicateurTableauDeBord {

    /** Cases with at least one step that is neither closed nor archived. */
    DOSSIERS_ACTIFS,

    /** Cases with no step still in progress: the exact complement of {@link #DOSSIERS_ACTIFS}. */
    AFFAIRES_VIDEES,

    /** Hearings in the planned state, regardless of date. */
    AUDIENCES_PLANIFIEES,

    /** Fee requests neither paid nor rejected, i.e. still awaiting a decision. */
    FRAIS_EN_ATTENTE,

    /**
     * Triggered, unprocessed extension alerts.
     *
     * <p>One row per alert, not per deliberation, as the counter counts: an extension notifying
     * several recipients produces several alerts, so the same case may appear more than once.
     */
    PROROGATIONS_ALERTE,

    /** Favourable deliberations, within the same category scope as the counter. */
    LITIGES_GAGNES,

    /** Unfavourable deliberations. */
    LITIGES_PERDUS,

    /** Mixed deliberations: neither won nor lost. */
    DECISIONS_MIXTES,

    /**
     * Adjudications carrying a provision reversal.
     *
     * <p>The only monetary indicator: the invariant is that the sum of listed amounts equals the
     * displayed amount, not the row count.
     */
    PROVISIONS_REPRISES;

    /** True for the only monetary indicator: the screen sums it instead of counting. */
    public boolean estUnMontant() {
        return this == PROVISIONS_REPRISES;
    }
}
