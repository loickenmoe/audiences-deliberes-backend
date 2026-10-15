package com.afriland.audiences.reporting.web.dto;

import java.time.LocalDate;

/**
 * Une ligne derrière un bloc du tableau de bord (Q-137).
 *
 * <p><b>Une seule forme de ligne pour les neuf indicateurs</b>, et c'est un choix. Les objets
 * comptés sont de natures différentes - des dossiers, des audiences, des demandes de frais, des
 * délibérés, des alertes, des adjudications - et neuf formes de réponse auraient donné neuf
 * tableaux à l'écran, chacun avec ses colonnes et ses défauts. Une forme commune se lit d'un coup
 * d'œil, et le bloc d'où l'on vient dit déjà ce qu'on regarde.
 *
 * @param id               l'identifiant de l'objet compté, pour que la ligne ait une clé stable.
 * @param dossierId        {@code null} pour un objet qui n'en dépend pas - une alerte hors dossier.
 *                         La ligne reste affichée sans lien : la masquer ferait que le nombre de
 *                         lignes ne tombe plus sur le nombre affiché.
 * @param referenceDossier la référence lisible du dossier, {@code null} s'il a été supprimé depuis.
 * @param intitule         ce qui nomme la ligne : le client pour un dossier, la juridiction pour une
 *                         audience, la référence de facture pour des frais.
 * @param precision        le second renseignement utile : l'étape en cours, le statut, l'avocat.
 *                         {@code null} quand il n'y a rien d'utile à ajouter.
 * @param date             la date qui situe la ligne. {@code null} quand l'objet n'en porte pas.
 * @param montant          renseigné pour les frais et les provisions, {@code null} ailleurs : une
 *                         colonne de montants vides invite à chercher un chiffre qui n'existe pas.
 */
public record DetailIndicateurResponse(
        Long id,
        Long dossierId,
        String referenceDossier,
        String intitule,
        String precision,
        LocalDate date,
        Double montant) {
}
