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
 * Filtres optionnels de {@code GET /dossiers}.
 * <p>
 * {@code reference}, {@code clientId} et {@code juristeId} ont été ajoutés au jalon M16 (Q-72) :
 * l'endpoint n'exposait que {@code nature}, {@code categorie} et {@code juridiction}, si bien qu'un
 * juriste suivant plusieurs dizaines de dossiers n'avait aucun moyen de retrouver le sien. Filtrer
 * côté client n'était pas une option : sur une liste paginée, cela n'aurait cherché que dans la
 * page affichée et aurait donc renvoyé des résultats faux.
 * <p>
 * {@code estSensible} et {@code sensibiliteStatut} ont été ajoutés au jalon M22 (Q-101), à la
 * demande du porteur du projet. Le second ne sert pas qu'au confort : {@code EN_ATTENTE} donne au DJ
 * et à la DJA la **file des sensibilités à valider**, que rien n'exposait — c'est la moitié de QF-08,
 * que j'avais close à tort en concluant que la notification suffisait. Une notification prévient
 * d'un événement ; elle ne dit pas ce qui reste en attente.
 */
public final class DossierSpecifications {

    private DossierSpecifications() {
    }

    /**
     * @param typeEtape    ne garde que les dossiers portant une étape de ce type
     * @param statutEtape  ne garde que les dossiers portant une étape à ce statut
     * @param dateDebut    début de la fenêtre d'audiences, incluse ; {@code null} pour ne pas borner
     * @param dateFin      fin de la fenêtre d'audiences, incluse
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
            // Recherche partielle : une référence se retient rarement en entier.
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
            /*
             * L'étape. Les deux critères portent sur la **même** étape : « un appel en cours » est
             * une question précise, alors que « un appel, et par ailleurs quelque chose en cours »
             * n'en est pas une. Cela répond aussi au cas des deux cassations concurrentes — un
             * dossier porté devant la Cour Suprême et devant la CCJA remonte dans les deux filtres,
             * ce qui est exact : les deux procédures existent.
             *
             * EXISTS et non jointure, pour la même raison que « mes dossiers » : une jointure ferait
             * remonter un dossier autant de fois qu'il a d'étapes, et fausserait le total paginé.
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
             * La période porte sur les **audiences** (arbitrage du métier) : « les dossiers qui ont
             * eu une audience entre ces deux dates ». Les audiences annulées en sont exclues — elles
             * ne se sont pas tenues, et les compter ferait remonter des dossiers restés muets sur la
             * période.
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
