package com.afriland.audiences.alerte.repository;

import com.afriland.audiences.referentiel.domain.CategorieDossier;

import java.util.List;

import com.afriland.audiences.alerte.domain.Alerte;
import com.afriland.audiences.alerte.domain.StatutAlerte;
import com.afriland.audiences.alerte.domain.StatutDiffusion;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AlerteRepository extends JpaRepository<Alerte, Long> {

    Page<Alerte> findByDestinataireId(Long destinataireId, Pageable pageable);

    Page<Alerte> findByDestinataireIdAndStatut(Long destinataireId, StatutAlerte statut, Pageable pageable);

    /**
     * The current user's notifications, with optional filters.
     * <p>The recipient is <b>not</b> optional: it comes from the token, never from a parameter,
     * otherwise anyone could read other users' notifications.
     */
    @Query("SELECT a FROM Alerte a WHERE a.destinataire.id = :destinataireId "
            + "AND (:statut IS NULL OR a.statut = :statut) "
            + "AND (:type IS NULL OR a.type = :type) "
            + "AND (:dossierId IS NULL OR a.dossierId = :dossierId)")
    Page<Alerte> rechercher(@Param("destinataireId") Long destinataireId,
                            @Param("statut") StatutAlerte statut,
                            @Param("type") TypeAlerte type,
                            @Param("dossierId") Long dossierId,
                            Pageable pageable);

    /**
     * Duplicate guard for scheduled alerts (e.g. {@code CHARGE_MAX_AVOCAT}): a still
     * {@code DECLENCHEE} alert with the same marker (stable prefix of {@code declencheur}, e.g.
     * {@code "[avocatId:42] "}) means the subject is already notified.
     */
    boolean existsByTypeAndDestinataireIdAndStatutAndDeclencheurStartingWith(
            TypeAlerte type, Long destinataireId, StatutAlerte statut, String marqueur);

    /**
     * Duplicate guard for an event to be reported <b>only once in its lifetime</b> (e.g. J-7
     * hearing reminder, alarm due date): unlike
     * {@link #existsByTypeAndDestinataireIdAndStatutAndDeclencheurStartingWith}, a {@code TRAITEE} status
     * does not allow raising it again.
     */
    boolean existsByTypeAndDestinataireIdAndDeclencheurStartingWith(
            TypeAlerte type, Long destinataireId, String marqueur);

    /**
     * FR-REP-02 (dashboard): unhandled extension alerts, all recipients. The category is read
     * through a subquery since {@code dossierId} is a plain reference; every extension alert
     * carries one, so the category partition is complete.
     */
    @Query("SELECT COUNT(a) FROM Alerte a WHERE a.type = :type AND a.statut = :statut "
            + "AND a.dossierId IN (SELECT d.id FROM Dossier d WHERE d.categorie IN :categories)")
    long compterParTypeEtStatut(@Param("type") TypeAlerte type,
                                @Param("statut") StatutAlerte statut,
                                @Param("categories") List<CategorieDossier> categories);

    /**
     * The alerts counted by {@link #countByTypeAndStatut}, most recent first. One row per alert,
     * not per case, so the list matches the displayed count.
     */
    @Query("SELECT a FROM Alerte a WHERE a.type = :type AND a.statut = :statut "
            + "AND a.dossierId IN (SELECT d.id FROM Dossier d WHERE d.categorie IN :categories) "
            + "ORDER BY a.id DESC")
    Page<Alerte> listerParTypeEtStatut(@Param("type") TypeAlerte type,
                                       @Param("statut") StatutAlerte statut,
                                       @Param("categories") List<CategorieDossier> categories,
                                       Pageable pageable);

    /**
     * In-app channel monitoring (NF-ALR-02): number of alerts with a given outcome in a window. One
     * count per outcome rather than a {@code GROUP BY}, so a missing outcome cannot be misread as a
     * measured zero.
     * <p>The upper bound is <b>exclusive</b>: the caller passes the day after the end date.
     */
    @Query("SELECT COUNT(a) FROM Alerte a WHERE a.statutDiffusion = :statut "
            + "AND a.dateDeclenchement >= :debut AND a.dateDeclenchement < :fin")
    long compterParDiffusion(@Param("statut") StatutDiffusion statut,
                                @Param("debut") LocalDateTime debut,
                                @Param("fin") LocalDateTime fin);

    /**
     * Alerts of the window nobody opened. Not part of any rate: an alert delivered but never read
     * is a different problem from delivery.
     */
    @Query("SELECT COUNT(a) FROM Alerte a WHERE a.statut = com.afriland.audiences.alerte.domain.StatutAlerte.DECLENCHEE "
            + "AND a.dateDeclenchement >= :debut AND a.dateDeclenchement < :fin")
    long compterNonTraitees(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);
}
