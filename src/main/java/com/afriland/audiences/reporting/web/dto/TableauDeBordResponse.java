package com.afriland.audiences.reporting.web.dto;

/** Real-time indicators across all cases (FR-REP-02, US 8.5). */
public record TableauDeBordResponse(
        long nbDossiersActifs,
        long nbAudiencesPlanifiees,
        long nbDemandesFraisEnAttente,
        long nbProrogationsAlerte,
        double tauxSuccesRecouvrement,
        /**
         * Won, lost and mixed disputes, given separately: "lost" is not "total minus won", since a
         * mixed deliberation is neither.
         */
        long nbLitigesGagnes,
        long nbLitigesPerdus,
        long nbLitigesMixtes,
        /**
         * Closed matters: cases with no step still in progress. Exact complement of
         * {@code nbDossiersActifs}; together they make up all cases with at least one step.
         */
        long nbAffairesVidees,
        double provisionsReprises) {
}
