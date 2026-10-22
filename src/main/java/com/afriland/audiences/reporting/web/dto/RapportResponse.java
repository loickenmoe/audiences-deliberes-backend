package com.afriland.audiences.reporting.web.dto;

import com.afriland.audiences.reporting.domain.TypeRapport;

import java.time.LocalDate;

/**
 * Periodic report (FR-REP-01, US 8.4): dashboard-derived indicators restricted to the requested
 * period.
 */
public record RapportResponse(
        TypeRapport type,
        LocalDate dateDebut,
        LocalDate dateFin,
        long nbNouveauxDossiers,
        long nbAudiencesTenues,
        long nbDemandesFraisDeposees,
        long nbDeliberesEnregistres,
        /**
         * Still computed and returned for backward compatibility, but no longer displayed or
         * exported: the counts below are shown instead.
         */
        double tauxSuccesRecouvrement,
        /**
         * Won, lost and mixed disputes for the period. "Lost" is not "total minus won": a mixed
         * deliberation is neither.
         */
        long nbLitigesGagnes,
        long nbLitigesPerdus,
        long nbLitigesMixtes,
        double provisionsReprises) {

    /** RG-REP-02: no data for the period; the controller returns 204. */
    public boolean estVide() {
        return nbNouveauxDossiers == 0 && nbAudiencesTenues == 0 && nbDemandesFraisDeposees == 0 && nbDeliberesEnregistres == 0;
    }
}
