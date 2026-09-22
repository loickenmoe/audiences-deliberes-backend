package com.afriland.audiences.common.audit;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Lecture filtrée de l'historique (Q-110).
 *
 * <p><b>Écrite générale, elle a d'abord servi partiellement — et c'est ce qui a payé.</b> En Q-110,
 * seul {@code GET /historique/systeme} l'employait, avec le périmètre figé sur les actions sans
 * dossier. L'écran d'audit de Q-112 est la même lecture sans cette restriction : il n'a pas fallu
 * écrire un second chemin, seulement cesser d'en brider un.
 *
 * <p>Le {@link Perimetre} reste une énumération exclusive plutôt qu'un couple de booléens : c'est ce
 * qui empêche qu'une consultation se retrouve, par la combinaison de deux drapeaux, dans un état que
 * personne n'a voulu.
 *
 * <p><b>Une spécification et non une requête à paramètres facultatifs</b>, pour la raison établie en
 * Q-103 : la forme {@code :param IS NULL OR colonne = :param} échoue sur PostgreSQL dès qu'un
 * paramètre est nul — le pilote ne peut pas typer un {@code null} — et le défaut ne se manifeste que
 * <b>lorsque le filtre n'est pas utilisé</b>, c'est-à-dire dans le cas le plus courant. Un critère
 * absent n'ajoute ici aucun prédicat, donc aucun paramètre à typer.
 */
public final class HistoriqueActionSpecifications {

    private HistoriqueActionSpecifications() {
    }

    /** Périmètre d'une consultation. Les trois valeurs sont exclusives et doivent le rester. */
    public enum Perimetre {
        /** Actions rattachées à aucun dossier : paramètres système, référentiels. */
        HORS_DOSSIER,
        /** Actions rattachées à un dossier précis ; exige {@code dossierId}. */
        UN_DOSSIER,
        /** Tout l'historique. Ouvert en Q-112 à l'écran d'audit, réservé au DJ et à la DJA. */
        TOUT
    }

    public static Specification<HistoriqueAction> withFilters(Perimetre perimetre, Long dossierId,
                                                                Long utilisateurId, TypeActionHistorique code,
                                                                LocalDate dateDebut, LocalDate dateFin) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();

            switch (perimetre) {
                case HORS_DOSSIER -> predicate = cb.and(predicate, cb.isNull(root.get("dossierId")));
                case UN_DOSSIER -> predicate = cb.and(predicate, cb.equal(root.get("dossierId"), dossierId));
                case TOUT -> { /* aucun prédicat de périmètre */ }
            }

            if (utilisateurId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("utilisateur").get("id"), utilisateurId));
            }

            /*
             * Depuis Q-111, le filtre porte sur le **code typé** et non plus sur la phrase. C'était
             * le point faible de Q-110 : `action` est un texte libre, et l'égalité stricte qu'on y
             * appliquait cessait de trouver la ligne dès qu'un libellé changeait d'un mot. Le code,
             * lui, est contraint par la base et par l'énumération.
             */
            if (code != null) {
                predicate = cb.and(predicate, cb.equal(root.get("code"), code));
            }

            if (dateDebut != null) {
                predicate = cb.and(predicate,
                        cb.greaterThanOrEqualTo(root.get("dateAction"), dateDebut.atStartOfDay()));
            }
            // Borne haute au début du lendemain : la journée de fin est incluse en entier, sans
            // dépendre de la précision des horodatages.
            if (dateFin != null) {
                predicate = cb.and(predicate,
                        cb.lessThan(root.get("dateAction"), dateFin.plusDays(1).atStartOfDay()));
            }

            return predicate;
        };
    }
}
