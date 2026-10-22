package com.afriland.audiences.reporting.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Each user's activity over a period.
 *
 * <p>Indicators, not a score: no rating, ranking or global total. "Cases won per person" is
 * deliberately absent (the outcome belongs to the case, not to whoever entered it), as is "time
 * spent" (the application timestamps actions, not work durations).
 */
public record RapportNominatifResponse(
        LocalDate dateDebut,
        LocalDate dateFin,
        List<ActiviteNominative> lignes) {

    /** RG-REP-02: no activity for the period; the controller returns 204. */
    public boolean estVide() {
        return lignes.isEmpty();
    }

    /**
     * What one person did over the period.
     *
     * @param delaiMoyenSaisieCompteRendu average days between a hearing and the entry of its
     *                                    report; null if none was entered (zero would read as
     *                                    same-day entry)
     */
    public record ActiviteNominative(
            Long utilisateurId,
            String nom,
            String profil,
            long dossiersOuverts,
            long audiencesPlanifiees,
            long comptesRendusSaisis,
            long deliberesEnregistres,
            long lettresRedigees,
            long fraisTraites,
            Double delaiMoyenSaisieCompteRendu) {
    }
}
