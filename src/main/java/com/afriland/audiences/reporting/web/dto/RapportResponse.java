package com.afriland.audiences.reporting.web.dto;

import com.afriland.audiences.reporting.domain.TypeRapport;

import java.time.LocalDate;

/**
 * FR-REP-01/US 8.4 : aucun champ n'est nommé par le contrat (contrairement à FR-REP-02) — jeu
 * d'indicateurs dérivé de ceux du tableau de bord, mais restreint à la période demandée (Q-63).
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
         * Le taux reste calculé et renvoyé, mais <b>plus affiché ni exporté</b> depuis le
         * 2026-10-06 : « le métier veut les chiffres, le montant, pas les pourcentages ». Les
         * trois décomptes ci-dessous disent ce qu'il résumait, et le disent sans calcul. Il n'est
         * pas retiré du contrat : la même requête le produit en comptant les résultats, et le
         * supprimer serait une rupture pour un consommateur qui le lirait encore.
         */
        double tauxSuccesRecouvrement,
        /**
         * Les litiges <b>gagnés</b>, <b>perdus</b> et <b>mixtes</b> de la période (métier,
         * 2026-10-06).
         *
         * <p>« Perdus » n'est pas « total moins gagnés » : un délibéré mixte n'est ni l'un ni
         * l'autre, et le déduire aurait compté les affaires partiellement gagnées comme des pertes.
         */
        long nbLitigesGagnes,
        long nbLitigesPerdus,
        long nbLitigesMixtes,
        double provisionsReprises) {

    /** RG-REP-02 : aucune donnée sur la période — 204 côté contrôleur. */
    public boolean estVide() {
        return nbNouveauxDossiers == 0 && nbAudiencesTenues == 0 && nbDemandesFraisDeposees == 0 && nbDeliberesEnregistres == 0;
    }
}
