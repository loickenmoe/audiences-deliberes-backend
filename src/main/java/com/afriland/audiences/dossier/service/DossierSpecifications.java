package com.afriland.audiences.dossier.service;

import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.DossierJuriste;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Optional filters of {@code GET /dossiers}. Filtering must be server-side: client-side filtering
 * on a paginated list would only search the displayed page.
 * <p>
 * {@code sensibiliteStatut = EN_ATTENTE} gives DJ and DJA the queue of sensitivities awaiting
 * validation.
 */
public final class DossierSpecifications {

    private static final String ATTRIBUT_DOSSIER = "dossier";

    private DossierSpecifications() {
    }

    /**
     * @param filtres   criteria received from the client
     * @param juristeId "my cases": keeps only cases assigned to this in-house lawyer; {@code null}
     *                  for no filter
     */
    public static Specification<Dossier> withFilters(FiltresDossiers filtres, Long juristeId) {
        return (root, query, cb) -> {
            var predicate = ajouterCriteresDuDossier(filtres, root, cb, cb.conjunction());
            if (query != null) {
                predicate = ajouterAffectation(juristeId, root, query, cb, predicate);
                predicate = ajouterEtape(filtres, root, query, cb, predicate);
                predicate = ajouterPeriodeDAudience(filtres, root, query, cb, predicate);
            }
            return predicate;
        };
    }

    /** Criteria held by the case itself. */
    private static Predicate ajouterCriteresDuDossier(FiltresDossiers filtres, Root<Dossier> root,
                                                      CriteriaBuilder cb, Predicate predicate) {
        if (StringUtils.hasText(filtres.nature())) {
            predicate = cb.and(predicate,
                    cb.equal(cb.lower(root.get("nature")), filtres.nature().toLowerCase()));
        }
        if (filtres.categorie() != null) {
            predicate = cb.and(predicate, cb.equal(root.get("categorie"), filtres.categorie()));
        }
        if (StringUtils.hasText(filtres.juridiction())) {
            predicate = cb.and(predicate, cb.like(cb.lower(root.get("juridictionSaisie")),
                    "%" + filtres.juridiction().toLowerCase() + "%"));
        }
        // Partial match: a reference is rarely remembered in full.
        if (StringUtils.hasText(filtres.reference())) {
            predicate = cb.and(predicate, cb.like(cb.lower(root.get("reference")),
                    "%" + filtres.reference().toLowerCase() + "%"));
        }
        if (filtres.clientId() != null) {
            predicate = cb.and(predicate, cb.equal(root.get("client").get("id"), filtres.clientId()));
        }
        if (filtres.estSensible() != null) {
            predicate = cb.and(predicate, cb.equal(root.get("estSensible"), filtres.estSensible()));
        }
        if (filtres.sensibiliteStatut() != null) {
            predicate = cb.and(predicate,
                    cb.equal(root.get("sensibiliteStatut"), filtres.sensibiliteStatut()));
        }
        return predicate;
    }

    /**
     * "My cases". EXISTS subquery rather than a join: a join would return a case once per
     * in-house lawyer and distort the pagination total.
     */
    private static Predicate ajouterAffectation(Long juristeId, Root<Dossier> root, CriteriaQuery<?> query,
                                                CriteriaBuilder cb, Predicate predicate) {
        if (juristeId == null) {
            return predicate;
        }
        var sousRequete = query.subquery(Long.class);
        var affectation = sousRequete.from(DossierJuriste.class);
        sousRequete.select(cb.literal(1L)).where(
                cb.equal(affectation.get(ATTRIBUT_DOSSIER).get("id"), root.get("id")),
                cb.equal(affectation.get("utilisateur").get("id"), juristeId));
        return cb.and(predicate, cb.exists(sousRequete));
    }

    /**
     * Both criteria apply to the same step ("an appeal in progress"). A case before both the
     * Supreme Court and the CCJA matches both filters, which is correct.
     * EXISTS rather than a join, for the same pagination reason as "my cases".
     */
    private static Predicate ajouterEtape(FiltresDossiers filtres, Root<Dossier> root, CriteriaQuery<?> query,
                                          CriteriaBuilder cb, Predicate predicate) {
        if (filtres.typeEtape() == null && filtres.statutEtape() == null) {
            return predicate;
        }
        var sousRequete = query.subquery(Long.class);
        var etape = sousRequete.from(EtapeProcedure.class);
        var conditions = cb.equal(etape.get(ATTRIBUT_DOSSIER).get("id"), root.get("id"));
        if (filtres.typeEtape() != null) {
            conditions = cb.and(conditions, cb.equal(etape.get("type"), filtres.typeEtape()));
        }
        if (filtres.statutEtape() != null) {
            conditions = cb.and(conditions, cb.equal(etape.get("statutCycleVie"), filtres.statutEtape()));
        }
        sousRequete.select(cb.literal(1L)).where(conditions);
        return cb.and(predicate, cb.exists(sousRequete));
    }

    /**
     * The period applies to hearings: cases with a hearing between the two dates.
     * Cancelled hearings are excluded since they were not held.
     */
    private static Predicate ajouterPeriodeDAudience(FiltresDossiers filtres, Root<Dossier> root,
                                                     CriteriaQuery<?> query, CriteriaBuilder cb,
                                                     Predicate predicate) {
        if (filtres.dateDebut() == null && filtres.dateFin() == null) {
            return predicate;
        }
        var sousRequete = query.subquery(Long.class);
        var audience = sousRequete.from(Audience.class);
        var conditions = cb.and(
                cb.equal(audience.get(ATTRIBUT_DOSSIER).get("id"), root.get("id")),
                cb.notEqual(audience.get("statut"), StatutAudience.ANNULEE));
        if (filtres.dateDebut() != null) {
            conditions = cb.and(conditions,
                    cb.greaterThanOrEqualTo(audience.get("datePlanifiee"), filtres.dateDebut()));
        }
        if (filtres.dateFin() != null) {
            conditions = cb.and(conditions,
                    cb.lessThanOrEqualTo(audience.get("datePlanifiee"), filtres.dateFin()));
        }
        sousRequete.select(cb.literal(1L)).where(conditions);
        return cb.and(predicate, cb.exists(sousRequete));
    }
}
