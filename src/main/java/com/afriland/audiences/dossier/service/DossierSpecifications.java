package com.afriland.audiences.dossier.service;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.DossierJuriste;
import com.afriland.audiences.referentiel.domain.CategorieDossier;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Filtres optionnels de {@code GET /dossiers}.
 * <p>
 * {@code reference}, {@code clientId} et {@code juristeId} ont été ajoutés au jalon M16 (Q-72) :
 * l'endpoint n'exposait que {@code nature}, {@code categorie} et {@code juridiction}, si bien qu'un
 * juriste suivant plusieurs dizaines de dossiers n'avait aucun moyen de retrouver le sien. Filtrer
 * côté client n'était pas une option : sur une liste paginée, cela n'aurait cherché que dans la
 * page affichée et aurait donc renvoyé des résultats faux.
 */
public final class DossierSpecifications {

    private DossierSpecifications() {
    }

    public static Specification<Dossier> withFilters(String nature, CategorieDossier categorie,
                                                     String juridiction, String reference,
                                                     Long clientId, Long juristeId) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (StringUtils.hasText(nature)) {
                predicate = cb.and(predicate, cb.equal(cb.lower(root.get("nature")), nature.toLowerCase()));
            }
            if (categorie != null) {
                predicate = cb.and(predicate, cb.equal(root.get("categorie"), categorie));
            }
            if (StringUtils.hasText(juridiction)) {
                predicate = cb.and(predicate,
                        cb.like(cb.lower(root.get("juridictionSaisie")), "%" + juridiction.toLowerCase() + "%"));
            }
            // Recherche partielle : une référence se retient rarement en entier.
            if (StringUtils.hasText(reference)) {
                predicate = cb.and(predicate,
                        cb.like(cb.lower(root.get("reference")), "%" + reference.toLowerCase() + "%"));
            }
            if (clientId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("client").get("id"), clientId));
            }
            /*
             * « Mes dossiers ». Sous-requête EXISTS plutôt que jointure : `Dossier` ne mappe aucune
             * collection d'affectations, et surtout une jointure ferait remonter un dossier autant
             * de fois qu'il a de juristes — ce qui fausserait le total de la pagination. EXISTS
             * répond à la seule question posée : ce juriste est-il affecté ?
             */
            if (juristeId != null && query != null) {
                var sousRequete = query.subquery(Long.class);
                var affectation = sousRequete.from(DossierJuriste.class);
                sousRequete.select(cb.literal(1L)).where(
                        cb.equal(affectation.get("dossier").get("id"), root.get("id")),
                        cb.equal(affectation.get("utilisateur").get("id"), juristeId));
                predicate = cb.and(predicate, cb.exists(sousRequete));
            }
            return predicate;
        };
    }
}
