package com.afriland.audiences.frais.service;

import com.afriland.audiences.frais.domain.DemandeFrais;
import com.afriland.audiences.frais.domain.StatutCircuitFrais;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Optional filters of {@code GET /frais/demandes}.
 *
 * <p>A specification is used rather than a query with optional parameters: the form
 * {@code :param IS NULL OR column = :param} fails on PostgreSQL whenever a parameter is null (the
 * JDBC driver cannot infer the type of a {@code null}), i.e. in the most common case of an
 * unfiltered list. Here an absent criterion adds no predicate, hence no parameter to type.
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
             * `avocatId` is not just a convenience filter: for a lawyer, the service forces it to their own
             * id. A lawyer must never read a colleague's requests.
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
            // Partial match: an invoice reference is rarely remembered in full.
            if (StringUtils.hasText(referenceFacture)) {
                predicate = cb.and(predicate, cb.like(cb.lower(root.get("referenceFacture")),
                        "%" + referenceFacture.toLowerCase() + "%"));
            }
            if (dateDebut != null) {
                predicate = cb.and(predicate,
                        cb.greaterThanOrEqualTo(root.get("createdAt"), dateDebut.atStartOfDay()));
            }
            // Inclusive upper bound: a request filed at 4 pm on the last day of the period belongs to it.
            // Comparing with `dateFin.atStartOfDay()` would exclude that whole day.
            if (dateFin != null) {
                predicate = cb.and(predicate,
                        cb.lessThanOrEqualTo(root.get("createdAt"), dateFin.atTime(LocalTime.MAX)));
            }
            return predicate;
        };
    }
}
