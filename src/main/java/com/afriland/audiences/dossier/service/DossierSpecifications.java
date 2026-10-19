package com.afriland.audiences.dossier.service;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.DossierJuriste;
import com.afriland.audiences.dossier.domain.StatutValidation;
import com.afriland.audiences.referentiel.domain.CategorieDossier;
import org.springframework.data.jpa.domain.Specification;
import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.domain.TypeEtape;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

/**
 * Optional filters of {@code GET /dossiers}. Filtering must be server-side: client-side filtering
 * on a paginated list would only search the displayed page.
 * <p>
 * {@code sensibiliteStatut = EN_ATTENTE} gives DJ and DJA the queue of sensitivities awaiting
 * validation.
 */
public final class DossierSpecifications {

    private DossierSpecifications() {
    }

    /**
     * @param typeEtape    keeps only cases with a step of this type
     * @param statutEtape  keeps only cases with a step in this status
     * @param dateDebut    start of the hearing window, inclusive; {@code null} for no bound
     * @param dateFin      end of the hearing window, inclusive
     */
    public static Specification<Dossier> withFilters(String nature, CategorieDossier categorie,
                                                     String juridiction, String reference,
                                                     Long clientId, Long juristeId,
                                                     Boolean estSensible, StatutValidation sensibiliteStatut,
                                                     TypeEtape typeEtape, StatutCycleVie statutEtape,
                                                     LocalDate dateDebut, LocalDate dateFin) {
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
            // Partial match: a reference is rarely remembered in full.
            if (StringUtils.hasText(reference)) {
                predicate = cb.and(predicate,
                        cb.like(cb.lower(root.get("reference")), "%" + reference.toLowerCase() + "%"));
            }
            if (clientId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("client").get("id"), clientId));
            }
            if (estSensible != null) {
                predicate = cb.and(predicate, cb.equal(root.get("estSensible"), estSensible));
            }
            if (sensibiliteStatut != null) {
                predicate = cb.and(predicate, cb.equal(root.get("sensibiliteStatut"), sensibiliteStatut));
            }
            /*
             * "My cases". EXISTS subquery rather than a join: a join would return a case once per
             * in-house lawyer and distort the pagination total.
             */
            if (juristeId != null && query != null) {
                var sousRequete = query.subquery(Long.class);
                var affectation = sousRequete.from(DossierJuriste.class);
                sousRequete.select(cb.literal(1L)).where(
                        cb.equal(affectation.get("dossier").get("id"), root.get("id")),
                        cb.equal(affectation.get("utilisateur").get("id"), juristeId));
                predicate = cb.and(predicate, cb.exists(sousRequete));
            }
            /*
             * Both criteria apply to the same step ("an appeal in progress"). A case before both
             * the Supreme Court and the CCJA matches both filters, which is correct.
             * EXISTS rather than a join, for the same pagination reason as "my cases".
             */
            if ((typeEtape != null || statutEtape != null) && query != null) {
                var sousRequete = query.subquery(Long.class);
                var etape = sousRequete.from(EtapeProcedure.class);
                var conditions = cb.equal(etape.get("dossier").get("id"), root.get("id"));
                if (typeEtape != null) {
                    conditions = cb.and(conditions, cb.equal(etape.get("type"), typeEtape));
                }
                if (statutEtape != null) {
                    conditions = cb.and(conditions, cb.equal(etape.get("statutCycleVie"), statutEtape));
                }
                sousRequete.select(cb.literal(1L)).where(conditions);
                predicate = cb.and(predicate, cb.exists(sousRequete));
            }

            /*
             * The period applies to hearings: cases with a hearing between the two dates.
             * Cancelled hearings are excluded since they were not held.
             */
            if ((dateDebut != null || dateFin != null) && query != null) {
                var sousRequete = query.subquery(Long.class);
                var audience = sousRequete.from(Audience.class);
                var conditions = cb.and(
                        cb.equal(audience.get("dossier").get("id"), root.get("id")),
                        cb.notEqual(audience.get("statut"), StatutAudience.ANNULEE));
                if (dateDebut != null) {
                    conditions = cb.and(conditions,
                            cb.greaterThanOrEqualTo(audience.get("datePlanifiee"), dateDebut));
                }
                if (dateFin != null) {
                    conditions = cb.and(conditions,
                            cb.lessThanOrEqualTo(audience.get("datePlanifiee"), dateFin));
                }
                sousRequete.select(cb.literal(1L)).where(conditions);
                predicate = cb.and(predicate, cb.exists(sousRequete));
            }

            return predicate;
        };
    }
}
