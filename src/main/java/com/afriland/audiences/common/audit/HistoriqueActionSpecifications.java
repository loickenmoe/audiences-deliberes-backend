package com.afriland.audiences.common.audit;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

/**
 * Lecture filtrée de l'historique (Q-110).
 *
 * <p><b>Volontairement général, exposé partiellement.</b> Cette classe porte les quatre critères
 * d'une consultation d'historique — périmètre, auteur, libellé d'action, période — alors que le seul
 * point d'entrée actuel, {@code GET /historique/systeme}, n'en publie qu'une partie et fige le
 * périmètre sur les actions <b>sans dossier</b>. Ce n'est pas de l'anticipation gratuite : si une
 * vue transversale de l'historique est décidée plus tard, elle sera la même lecture sans la
 * restriction de périmètre, et non un second écran à écrire de zéro.
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

    /**
     * Périmètre d'une consultation. Les trois valeurs sont exclusives et doivent le rester : c'est
     * ce qui empêche qu'une vue « système » se mette un jour à renvoyer, par un paramètre oublié,
     * l'activité des juristes sur leurs dossiers.
     */
    public enum Perimetre {
        /** Actions rattachées à aucun dossier : paramètres système, référentiels. */
        HORS_DOSSIER,
        /** Actions rattachées à un dossier précis. */
        UN_DOSSIER,
        /** Tout l'historique. Aucun endpoint ne l'emprunte aujourd'hui (cf. Q-110). */
        TOUT
    }

    public static Specification<HistoriqueAction> withFilters(Perimetre perimetre, Long dossierId,
                                                                Long utilisateurId, String action,
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
             * Égalité stricte, jamais « contient ». `action` est une phrase française libre (QF-24),
             * parfois concaténée — « Modification de la configuration SEUIL_PROROGATIONS ». Un
             * `LIKE '%…%'` y paraîtrait commode et mentirait par omission dès qu'un libellé change
             * d'un mot. Le choix se fait donc dans une liste fermée, construite à partir des
             * libellés réellement présents ; le jour où un code d'action typé existera, c'est lui
             * qui alimentera cette liste.
             */
            if (StringUtils.hasText(action)) {
                predicate = cb.and(predicate, cb.equal(root.get("action"), action));
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
