package com.afriland.audiences.frais.service;

import com.afriland.audiences.frais.domain.DemandeFrais;
import com.afriland.audiences.frais.domain.StatutCircuitFrais;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Filtres optionnels de {@code GET /frais/demandes} (Q-103).
 *
 * <p><b>Pourquoi une spécification et non une requête à paramètres facultatifs.</b> La forme
 * {@code :param IS NULL OR colonne = :param} paraît naturelle, et elle échoue sur PostgreSQL dès
 * qu'un paramètre est nul : le pilote JDBC ne peut pas déduire le type d'un {@code null}, et la base
 * refuse la requête — {@code lower(bytea) does not exist}, puis
 * {@code could not determine data type of parameter}. Le défaut ne se manifeste que **lorsque le
 * filtre n'est pas utilisé**, c'est-à-dire dans le cas le plus courant : ouvrir la file des frais
 * sans rien chercher renvoyait une erreur 500.
 *
 * <p>Une spécification n'a pas ce problème par construction : un critère absent **n'ajoute aucun
 * prédicat**, donc aucun paramètre à typer. C'est aussi ce que fait déjà
 * {@link com.afriland.audiences.dossier.service.DossierSpecifications} — l'uniformité n'est pas ici
 * une question de style, c'est la seule forme qui résiste à l'ajout d'un filtre facultatif de plus.
 */
public final class DemandeFraisSpecifications {

    private DemandeFraisSpecifications() {
    }

    public static Specification<DemandeFrais> withFilters(Long avocatId, StatutCircuitFrais statut,
                                                          Long dossierId, String referenceFacture,
                                                          LocalDate dateDebut, LocalDate dateFin) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            /*
             * `avocatId` n'est pas un filtre de confort : pour un avocat, le service l'impose à son
             * propre identifiant. Un avocat ne doit jamais lire les demandes d'un confrère.
             */
            if (avocatId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("avocat").get("id"), avocatId));
            }
            if (statut != null) {
                predicate = cb.and(predicate, cb.equal(root.get("statutCircuit"), statut));
            }
            if (dossierId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("dossier").get("id"), dossierId));
            }
            // Recherche partielle : une référence de facture se retient rarement en entier.
            if (StringUtils.hasText(referenceFacture)) {
                predicate = cb.and(predicate, cb.like(cb.lower(root.get("referenceFacture")),
                        "%" + referenceFacture.toLowerCase() + "%"));
            }
            if (dateDebut != null) {
                predicate = cb.and(predicate,
                        cb.greaterThanOrEqualTo(root.get("createdAt"), dateDebut.atStartOfDay()));
            }
            // Borne haute **inclusive** : une demande déposée à 16 h le dernier jour de la période
            // en fait partie. Comparer à `dateFin.atStartOfDay()` exclurait toute la journée.
            if (dateFin != null) {
                predicate = cb.and(predicate,
                        cb.lessThanOrEqualTo(root.get("createdAt"), dateFin.atTime(LocalTime.MAX)));
            }
            return predicate;
        };
    }
}
