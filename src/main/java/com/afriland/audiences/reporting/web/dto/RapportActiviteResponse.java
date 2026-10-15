package com.afriland.audiences.reporting.web.dto;

import com.afriland.audiences.common.audit.TypeActionHistorique;

import java.time.LocalDate;
import java.util.List;

/**
 * La fiche d'activité d'une personne sur une période (observation 14, Q-133).
 *
 * <p><b>Pourquoi elle existe à côté du rapport nominatif.</b> Le tableau de la direction compare des
 * personnes : il tient sur six colonnes choisies, et c'est ce qui le rend lisible. Mais le métier a
 * demandé le 2026-10-09 que le rapport « reflète fidèlement ce que l'utilisateur a effectué » - et
 * six colonnes ne reflètent pas fidèlement l'activité d'une Assistante ou d'un supérieur
 * hiérarchique, dont le métier n'est pas d'ouvrir des dossiers. Les deux lectures sont légitimes et
 * incompatibles dans un même tableau : d'où deux niveaux, une seule source.
 *
 * <p><b>Une ligne par nature d'action réellement accomplie</b>, et non une colonne par nature
 * possible. Les natures sans aucune action ne figurent pas : quarante-neuf lignes à zéro diraient
 * moins que cinq lignes pleines, et transformeraient la fiche en inventaire de ce que la personne
 * n'a pas fait.
 *
 * <p><b>Ni note, ni total.</b> Comme le tableau nominatif (Q-132), et pour la même raison :
 * additionner des actions de natures différentes fabriquerait un chiffre de mérite que rien ne
 * justifie. Les lignes suivent l'ordre du <b>décompte décroissant</b> - ici, contrairement au
 * tableau de la direction, il n'y a qu'une personne, donc aucun palmarès possible entre collègues.
 *
 * @param delaiMoyenSaisieCompteRendu jours écoulés en moyenne entre une audience et la saisie de son
 *                                    compte rendu. {@code null}, jamais zéro, quand la personne n'en
 *                                    a saisi aucun : zéro se lirait comme une saisie le jour même,
 *                                    c'est-à-dire comme la meilleure performance possible.
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
     * Vide quand la personne n'a rien fait sur la période.
     *
     * <p>Le délai moyen ne compte pas : il ne peut exister sans compte rendu saisi, donc sans ligne.
     */
    public boolean estVide() {
        return lignes.isEmpty();
    }

    /**
     * Une nature d'action et son décompte.
     *
     * <p>Le <b>code</b> circule, pas son libellé : l'application est bilingue, et c'est le frontend
     * qui traduit (bloc {@code historique.actions}). Un libellé envoyé par le serveur serait
     * français pour tout le monde.
     */
    public record LigneActivite(TypeActionHistorique code, long total) {
    }
}
