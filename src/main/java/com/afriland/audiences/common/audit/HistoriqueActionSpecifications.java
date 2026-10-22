package com.afriland.audiences.common.audit;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Filtered history query. {@link Perimetre} is an exclusive enum rather than two booleans, so no
 * unintended flag combination is possible.
 *
 * <p>A Specification rather than a query with optional parameters: the
 * {@code :param IS NULL OR column = :param} form fails on PostgreSQL when a parameter is null (the
 * driver cannot type it). Here an absent criterion adds no predicate at all.
 */
public final class HistoriqueActionSpecifications {

    private HistoriqueActionSpecifications() {
    }

    /** Consultation perimeter; the three values are mutually exclusive. */
    public enum Perimetre {
        /** Actions not linked to any case: system settings, reference data. */
        HORS_DOSSIER,
        /** Actions on a specific case; requires {@code dossierId}. */
        UN_DOSSIER,
        /** Whole history; audit screen only, reserved to DJ and DJA. */
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
                case TOUT -> { /* no perimeter predicate */ }
            }

            if (utilisateurId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("utilisateur").get("id"), utilisateurId));
            }

            /*
             * Filter on the typed code, not on the free-text `action` label, which may change wording.
             */
            if (code != null) {
                predicate = cb.and(predicate, cb.equal(root.get("code"), code));
            }

            if (dateDebut != null) {
                predicate = cb.and(predicate,
                        cb.greaterThanOrEqualTo(root.get("dateAction"), dateDebut.atStartOfDay()));
            }
            // Exclusive upper bound at the start of the next day, so the whole end day is included.
            if (dateFin != null) {
                predicate = cb.and(predicate,
                        cb.lessThan(root.get("dateAction"), dateFin.plusDays(1).atStartOfDay()));
            }

            return predicate;
        };
    }
}
