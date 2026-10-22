package com.afriland.audiences.delibere.repository;

import com.afriland.audiences.referentiel.domain.CategorieDossier;

import com.afriland.audiences.delibere.domain.ResultatDelibere;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    /** Deliberations whose appeal deadline has passed and whose step is still DELIBERE_VIDE (no appeal lodged); used by DelaiRecoursScheduler. */
    List<Delibere> findByDateEcheanceRecoursNotNullAndDateEcheanceRecoursLessThanEqualAndEtapeStatutCycleVie(
            LocalDateTime maintenant, StatutCycleVie statutCycleVie);

    /**
     * Per-lawyer performance indicator (RG-INT-01, {@code GET /repertoire/avocats}): number of
     * {@code FAVORABLE} deliberations over all deliberations rendered on the cases the lawyer is assigned to.
     */
    @Query("SELECT da.avocat.id AS avocatId, "
            + "SUM(CASE WHEN d.resultat = com.afriland.audiences.delibere.domain.ResultatDelibere.FAVORABLE THEN 1L ELSE 0L END) AS favorables, "
            + "COUNT(d) AS total "
            + "FROM Delibere d JOIN com.afriland.audiences.dossier.domain.DossierAvocat da ON da.dossier = d.dossier "
            // A pending or reopened deliberation is neither a success nor a failure.
            + "WHERE d.resultat IS NOT NULL "
            + "GROUP BY da.avocat.id")
    List<PerformanceAvocat> calculerPerformanceParAvocat();

    interface PerformanceAvocat {
        Long getAvocatId();

        Long getFavorables();

        Long getTotal();
    }

    /**
     * FR-REP-02 (dashboard): debt-recovery performance over all periods.
     *
     * <p>The three results are counted separately: "lost" is not "total minus won", because a
     * {@code MIXTE} deliberation is neither; deducing it would count partial wins as failures.
     */
    @Query("SELECT SUM(CASE WHEN d.resultat = com.afriland.audiences.delibere.domain.ResultatDelibere.FAVORABLE THEN 1L ELSE 0L END) AS favorables, "
            + "SUM(CASE WHEN d.resultat = com.afriland.audiences.delibere.domain.ResultatDelibere.DEFAVORABLE THEN 1L ELSE 0L END) AS defavorables, "
            + "SUM(CASE WHEN d.resultat = com.afriland.audiences.delibere.domain.ResultatDelibere.MIXTE THEN 1L ELSE 0L END) AS mixtes, "
            + "COUNT(d) AS total FROM Delibere d "
            + "WHERE d.dossier.categorie IN :categories "
            + "AND d.resultat IS NOT NULL")
    PerformanceGlobale calculerResultats(@Param("categories") List<CategorieDossier> categories);

    /**
     * The deliberations counted by {@link #calculerTauxSuccesRecouvrement}, for a given result.
     *
     * <p>Applies the same two restrictions as the counter ({@code RECOUVREMENT} category and a result
     * set), so that the detail shows exactly what the counter counts.
     */
    @Query("SELECT d FROM Delibere d "
            + "WHERE d.dossier.categorie IN :categories "
            + "AND d.resultat = :resultat "
            + "ORDER BY d.dateDeliberee DESC, d.id DESC")
    Page<Delibere> listerParResultat(@Param("resultat") ResultatDelibere resultat,
                                     @Param("categories") List<CategorieDossier> categories,
                                     Pageable pageable);

    /** FR-REP-01 (periodic report): same indicators, restricted to deliberations rendered within the period. */
    @Query("SELECT SUM(CASE WHEN d.resultat = com.afriland.audiences.delibere.domain.ResultatDelibere.FAVORABLE THEN 1L ELSE 0L END) AS favorables, "
            + "SUM(CASE WHEN d.resultat = com.afriland.audiences.delibere.domain.ResultatDelibere.DEFAVORABLE THEN 1L ELSE 0L END) AS defavorables, "
            + "SUM(CASE WHEN d.resultat = com.afriland.audiences.delibere.domain.ResultatDelibere.MIXTE THEN 1L ELSE 0L END) AS mixtes, "
            + "COUNT(d) AS total FROM Delibere d "
            + "WHERE d.dossier.categorie IN :categories "
            + "AND d.resultat IS NOT NULL AND d.dateDeliberee BETWEEN :debut AND :fin")
    PerformanceGlobale calculerResultatsPeriode(@Param("debut") LocalDate debut,
                                                @Param("fin") LocalDate fin,
                                                @Param("categories") List<CategorieDossier> categories);

    interface PerformanceGlobale {
        Long getFavorables();

        /** Unfavourable deliberations: the cases <b>lost</b>, and only those. */
        Long getDefavorables();

        /** Neither won nor lost. Counting them separately is the only way not to misattribute them. */
        Long getMixtes();

        Long getTotal();
    }

    /** FR-REP-01 (periodic report): deliberations rendered within the period, excluding pending ones. */
    long countByDateDelibereeBetweenAndResultatIsNotNull(LocalDate debut, LocalDate fin);

    /**
     * FR-REP-01, by category. A deliberation without a result has not been rendered, hence the
     * condition on the result.
     */
    @Query("SELECT COUNT(d) FROM Delibere d WHERE d.resultat IS NOT NULL "
            + "AND d.dateDeliberee BETWEEN :debut AND :fin AND d.dossier.categorie IN :categories")
    long compterRendusEntre(@Param("debut") LocalDate debut,
                            @Param("fin") LocalDate fin,
                            @Param("categories") List<CategorieDossier> categories);

    /** Case file "Deliberations" tab, most recent first. */
    List<Delibere> findByDossierIdOrderByDateDelibereeDesc(Long dossierId);

    /** At most one pending deliberation (neither delivered nor reopened) per step. */
    boolean existsByEtapeIdAndResultatIsNullAndDateRabattementIsNull(Long etapeId);

    /**
     * The last <b>rendered</b> deliberation on a step.
     *
     * <p>A step may carry several (extended, reopened, re-deliberated); the last rendered one decides
     * what may follow: appeal or closure. Sorted by descending id (creation order), not by
     * deliberation date, which an extension can move.
     */
    Optional<Delibere> findFirstByEtapeIdAndResultatIsNotNullOrderByIdDesc(Long etapeId);
}
