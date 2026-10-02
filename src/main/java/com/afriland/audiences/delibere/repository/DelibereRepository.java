package com.afriland.audiences.delibere.repository;

import com.afriland.audiences.delibere.domain.Delibere;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DelibereRepository extends JpaRepository<Delibere, Long> {

    /** Délibérés dont le délai de recours est échu et dont l'étape est toujours DELIBERE_VIDE (aucun recours exercé) — DelaiRecoursScheduler. */
    List<Delibere> findByDateEcheanceRecoursNotNullAndDateEcheanceRecoursLessThanEqualAndEtapeStatutCycleVie(
            LocalDateTime maintenant, StatutCycleVie statutCycleVie);

    /**
     * Indicateur de performance par avocat (RG-INT-01, `GET /repertoire/avocats`, M9) : nombre de
     * délibérés {@code FAVORABLE} rapporté au nombre total de délibérés rendus sur les dossiers où
     * l'avocat est affecté — seule mesure de « succès » disponible avant le module Décisions
     * définitives (M12, Q-52).
     */
    @Query("SELECT da.avocat.id AS avocatId, "
            + "SUM(CASE WHEN d.resultat = com.afriland.audiences.delibere.domain.ResultatDelibere.FAVORABLE THEN 1L ELSE 0L END) AS favorables, "
            + "COUNT(d) AS total "
            + "FROM Delibere d JOIN com.afriland.audiences.dossier.domain.DossierAvocat da ON da.dossier = d.dossier "
            // Q-78 : un délibéré en attente ou rabattu n'est ni un succès ni un échec.
            + "WHERE d.resultat IS NOT NULL "
            + "GROUP BY da.avocat.id")
    List<PerformanceAvocat> calculerPerformanceParAvocat();

    interface PerformanceAvocat {
        Long getAvocatId();

        Long getFavorables();

        Long getTotal();
    }

    /** FR-REP-02 (tableau de bord, M13) : taux de succès recouvrement, toutes périodes confondues. */
    @Query("SELECT SUM(CASE WHEN d.resultat = com.afriland.audiences.delibere.domain.ResultatDelibere.FAVORABLE THEN 1L ELSE 0L END) AS favorables, "
            + "COUNT(d) AS total FROM Delibere d "
            + "WHERE d.dossier.categorie = com.afriland.audiences.referentiel.domain.CategorieDossier.RECOUVREMENT "
            + "AND d.resultat IS NOT NULL")
    PerformanceGlobale calculerTauxSuccesRecouvrement();

    /** FR-REP-01 (rapport périodique, M13) : même indicateur, restreint aux délibérés rendus dans la période. */
    @Query("SELECT SUM(CASE WHEN d.resultat = com.afriland.audiences.delibere.domain.ResultatDelibere.FAVORABLE THEN 1L ELSE 0L END) AS favorables, "
            + "COUNT(d) AS total FROM Delibere d "
            + "WHERE d.dossier.categorie = com.afriland.audiences.referentiel.domain.CategorieDossier.RECOUVREMENT "
            + "AND d.resultat IS NOT NULL AND d.dateDeliberee BETWEEN :debut AND :fin")
    PerformanceGlobale calculerTauxSuccesRecouvrementPeriode(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    interface PerformanceGlobale {
        Long getFavorables();

        Long getTotal();
    }

    /** FR-REP-01 (rapport périodique, M13) : délibérés rendus dans la période — hors mises en délibéré en attente (Q-78). */
    long countByDateDelibereeBetweenAndResultatIsNotNull(LocalDate debut, LocalDate fin);

    /** M15 : onglet « Délibérés » de la fiche dossier (Q-68), plus récent d'abord. */
    List<Delibere> findByDossierIdOrderByDateDelibereeDesc(Long dossierId);

    /** Q-78 : au plus un délibéré en attente (ni vidé, ni rabattu) par étape. */
    boolean existsByEtapeIdAndResultatIsNullAndDateRabattementIsNull(Long etapeId);

    /**
     * Le dernier délibéré <b>rendu</b> sur une étape (2026-09-29).
     *
     * <p>Une étape peut en porter plusieurs — prorogée, rabattue, remise en délibéré. C est le
     * dernier rendu qui décide de ce qui peut suivre : recours ou clôture. Tri par identifiant
     * décroissant, soit l ordre de création — et non par date délibérée, qu une prorogation déplace.
     */
    Optional<Delibere> findFirstByEtapeIdAndResultatIsNotNullOrderByIdDesc(Long etapeId);
}
