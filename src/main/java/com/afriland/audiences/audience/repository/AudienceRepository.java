package com.afriland.audiences.audience.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.afriland.audiences.referentiel.domain.CategorieDossier;

import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.StatutAudience;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AudienceRepository extends JpaRepository<Audience, Long> {

    /**
     * RG-AUD-06: duplicate hearing check. Called with {@code ANNULEE}: a cancelled hearing does not
     * count, so rescheduling on the same date after an adjournment is not a duplicate.
     */
    boolean existsByEtapeIdAndDatePlanifieeAndStatutNot(Long etapeId, LocalDate datePlanifiee, StatutAudience statut);

    /** Deliberation preconditions: a held hearing is required on the step. */
    boolean existsByEtapeIdAndStatut(Long etapeId, StatutAudience statut);

    /** Deliberation preconditions: no hearing may remain pending on the step. */
    long countByEtapeIdAndStatut(Long etapeId, StatutAudience statut);

    /**
     * FR-AUD-05: calendar and exports. Called with {@code ANNULEE} to exclude cancelled hearings.
     */
    List<Audience> findByDatePlanifieeBetweenAndStatutNot(LocalDate debut, LocalDate fin, StatutAudience statut);

    /** RG-AUD-03 (J-7/J-3/J-1 reminders): hearings still scheduled on a given date. */
    List<Audience> findByStatutAndDatePlanifiee(StatutAudience statut, LocalDate datePlanifiee);

    /**
     * RG-INT-11: hearings whose date has passed, cancelled ones excluded (no report to expect).
     * <p>The status is deliberately NOT restricted to {@code TENUE}: the in-house lawyer marks a
     * hearing held when entering their own report, and the lawyer's reminder must not wait for that
     * internal step.
     */
    List<Audience> findByDatePlanifieeBeforeAndStatutNot(LocalDate date, StatutAudience statut);

    /** Hearings of a given day, cancelled ones excluded: the hearing log (screen 44). Paginated. */
    Page<Audience> findByDatePlanifieeAndStatutNot(LocalDate datePlanifiee, StatutAudience statut, Pageable pageable);

    /**
     * Hearings of a window still in the requested status.
     * <p>Used to list expected reports: a past hearing still {@code PLANIFIEE} has, <b>by
     * construction</b>, no report yet, since entering the report moves it to {@code TENUE}.
     */
    Page<Audience> findByStatutAndDatePlanifieeBetween(
            StatutAudience statut, LocalDate debut, LocalDate fin, Pageable pageable);

    /** FR-REP-02 (dashboard): upcoming hearings, all cases. */
    @Query("SELECT COUNT(a) FROM Audience a "
            + "WHERE a.statut = :statut AND a.dossier.categorie IN :categories")
    long compterParStatut(@Param("statut") StatutAudience statut,
                          @Param("categories") List<CategorieDossier> categories);

    /** The hearings counted by {@link #countByStatut}, nearest first. */
    @Query("SELECT a FROM Audience a "
            + "WHERE a.statut = :statut AND a.dossier.categorie IN :categories "
            + "ORDER BY a.datePlanifiee ASC")
    Page<Audience> listerParStatut(@Param("statut") StatutAudience statut,
                                   @Param("categories") List<CategorieDossier> categories,
                                   Pageable pageable);

    /** FR-REP-01 (periodic report): hearings held in the period. */
    long countByStatutAndDateTenueBetween(StatutAudience statut, LocalDate debut, LocalDate fin);

    /** FR-REP-01, restricted by category. */
    @Query("SELECT COUNT(a) FROM Audience a WHERE a.statut = :statut "
            + "AND a.dateTenue BETWEEN :debut AND :fin AND a.dossier.categorie IN :categories")
    long compterTenuesEntre(@Param("statut") StatutAudience statut,
                            @Param("debut") LocalDate debut,
                            @Param("fin") LocalDate fin,
                            @Param("categories") List<CategorieDossier> categories);

    /** "Hearings" tab of the case page, most recent first. */
    Page<Audience> findByDossierIdOrderByDatePlanifieeDesc(Long dossierId, Pageable pageable);
}
