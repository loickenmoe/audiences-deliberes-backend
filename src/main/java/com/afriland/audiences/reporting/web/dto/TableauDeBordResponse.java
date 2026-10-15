package com.afriland.audiences.reporting.web.dto;

/** FR-REP-02/US 8.5 : indicateurs temps réel, tous dossiers confondus. */
public record TableauDeBordResponse(
        long nbDossiersActifs,
        long nbAudiencesPlanifiees,
        long nbDemandesFraisEnAttente,
        long nbProrogationsAlerte,
        double tauxSuccesRecouvrement,
        /**
         * Les litiges <b>gagnés</b>, <b>perdus</b> et <b>mixtes</b> (métier, 2026-10-03,
         * observation 12).
         *
         * <p>Le calcul les possédait déjà et n'exposait que le pourcentage : la Direction lisait un
         * taux sans savoir sur combien d'affaires il portait. Les trois sont donnés séparément, car
         * « perdus » n'est pas « total moins gagnés » - un délibéré mixte n'est ni l'un ni l'autre.
         */
        long nbLitigesGagnes,
        long nbLitigesPerdus,
        long nbLitigesMixtes,
        /**
         * Les <b>affaires vidées</b> : les dossiers dont plus aucune étape n'est en cours (métier,
         * 2026-10-06).
         *
         * <p>Complément exact de {@code nbDossiersActifs} : les deux s'additionnent pour faire les
         * dossiers qui portent au moins une étape.
         */
        long nbAffairesVidees,
        double provisionsReprises) {
}
