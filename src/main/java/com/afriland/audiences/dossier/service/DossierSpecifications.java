package com.afriland.audiences.dossier.service;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.referentiel.domain.CategorieDossier;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/** Filtres optionnels de {@code GET /dossiers} (nature, categorie, juridiction). */
public final class DossierSpecifications {

    private DossierSpecifications() {
    }

    public static Specification<Dossier> withFilters(String nature, CategorieDossier categorie, String juridiction) {
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
            return predicate;
        };
    }
}
