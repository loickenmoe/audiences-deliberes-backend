package com.afriland.audiences.reporting.web.dto;

import com.afriland.audiences.common.audit.TypeActionHistorique;

import java.time.LocalDate;
import java.util.List;

/**
 * One person's activity sheet over a period. Complements the per-user report (a fixed set of
 * columns comparing people) by reflecting everything the user actually did, from the same source.
 *
 * <p>One row per action type actually performed; types with no action are omitted. No score or
 * total. Rows are sorted by descending count (a single person, so no ranking between colleagues).
 *
 * @param delaiMoyenSaisieCompteRendu average days between a hearing and the entry of its report;
 *                                    {@code null}, never zero, when none was entered
 */
public record RapportActiviteResponse(
        Long utilisateurId,
        String nom,
        String profil,
        LocalDate dateDebut,
        LocalDate dateFin,
        List<LigneActivite> lignes,
        Double delaiMoyenSaisieCompteRendu) {

    /**
     * Empty when the person did nothing in the period. The average delay is ignored: it cannot
     * exist without an entered report, hence without a row.
     */
    public boolean estVide() {
        return lignes.isEmpty();
    }

    /**
     * An action type and its count. The code is sent, not a label: the application is bilingual
     * and the frontend translates it ({@code historique.actions}).
     */
    public record LigneActivite(TypeActionHistorique code, long total) {
    }
}
