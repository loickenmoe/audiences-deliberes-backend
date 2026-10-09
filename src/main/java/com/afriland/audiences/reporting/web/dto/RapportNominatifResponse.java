package com.afriland.audiences.reporting.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * L'activité de chacun sur une période (observation 14, N6).
 *
 * <p><b>Des indicateurs, pas une note.</b> L'application compte ; le responsable apprécie. C'est la
 * ligne arrêtée avec le métier le 2026-10-07, et elle gouverne ce que ce rapport contient comme ce
 * qu'il refuse de contenir : ni note, ni classement, ni total « global » qui ferait d'une somme
 * d'actions disparates un chiffre de mérite.
 *
 * <p><b>Deux indicateurs qu'on pourrait attendre et qui n'y sont pas.</b> Les « dossiers gagnés par
 * personne » : l'issue d'un délibéré appartient au dossier, pas à celui qui l'a saisi, et
 * l'attribuer au juriste qui a tapé la décision reviendrait à lui créditer le travail du tribunal.
 * Le « temps passé » : l'application horodate des actions, jamais des durées de travail - tout
 * chiffre de ce genre serait inventé.
 */
public record RapportNominatifResponse(
        LocalDate dateDebut,
        LocalDate dateFin,
        List<ActiviteNominative> lignes) {

    /** RG-REP-02 : aucune activité sur la période — 204 côté contrôleur, comme le rapport périodique. */
    public boolean estVide() {
        return lignes.isEmpty();
    }

    /**
     * Ce qu'une personne a fait, et sur combien de temps elle l'a fait.
     *
     * @param delaiMoyenSaisieCompteRendu jours écoulés en moyenne entre l'audience et la saisie de
     *                                    son compte rendu. <b>Nul</b> si elle n'en a saisi aucun :
     *                                    pas de moyenne sur rien, et zéro se lirait comme une
     *                                    saisie le jour même.
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
