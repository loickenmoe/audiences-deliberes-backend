package com.afriland.audiences.reporting.web.dto;

/** FR-REP-02/US 8.5 : indicateurs temps réel, tous dossiers confondus. */
public record TableauDeBordResponse(
        long nbDossiersActifs,
        long nbAudiencesPlanifiees,
        long nbDemandesFraisEnAttente,
        long nbProrogationsAlerte,
        double tauxSuccesRecouvrement,
        double provisionsReprises) {
}
