package com.afriland.audiences.reporting.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Lawyers' activity over a period, counterpart of the internal per-user report.
 *
 * <p>A separate table because lawyers perform none of the internal actions counted there (they file,
 * legal officers record). Lawyers are compared on what they actually do: filing reports, fee
 * requests and other publications, and answering explanation requests. No score, ranking or global
 * total; rows are ordered by name.
 *
 * <p>The four counted action types are lawyer-only ({@code hasRole('AVOCAT')}), so grouping on them
 * returns only lawyers without filtering on profile.
 */
public record RapportAvocatsResponse(LocalDate dateDebut, LocalDate dateFin, List<ActiviteAvocat> lignes) {

    /** Empty when no lawyer filed anything in the period: a 204, not a page of zeros. */
    public boolean estVide() {
        return lignes.isEmpty();
    }

    /**
     * A lawyer and their filings over the period.
     *
     * @param avocatId {@code null} if the lawyer has since been removed from the directory; their
     *                 filings are still counted and the UI opens them by user account instead
     * @param nom      directory name when still listed, account name otherwise
     */
    public record ActiviteAvocat(
            Long utilisateurId,
            Long avocatId,
            String nom,
            long comptesRendusDeposes,
            long demandesFraisDeposees,
            long autresPublicationsDeposees,
            long explicationsFournies) {
    }
}
