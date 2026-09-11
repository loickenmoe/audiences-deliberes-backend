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
        double tauxSuccesRecouvrement,
        double provisionsReprises) {

    /** RG-REP-02 : aucune donnée sur la période — 204 côté contrôleur. */
    public boolean estVide() {
        return nbNouveauxDossiers == 0 && nbAudiencesTenues == 0 && nbDemandesFraisDeposees == 0 && nbDeliberesEnregistres == 0;
    }
}
