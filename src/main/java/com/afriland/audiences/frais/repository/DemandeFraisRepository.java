package com.afriland.audiences.frais.repository;

import com.afriland.audiences.referentiel.domain.CategorieDossier;

import com.afriland.audiences.frais.domain.DemandeFrais;
import com.afriland.audiences.frais.domain.StatutCircuitFrais;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DemandeFraisRepository extends JpaRepository<DemandeFrais, Long>,
        JpaSpecificationExecutor<DemandeFrais> {

    /**
     * Duplicate invoice check (RG-INT-01, ERR-006): uniqueness is checked across all requests whatever
     * their status, including {@code REJETEE} ones (US 4.3), consistent with the SQL {@code UNIQUE}
     * constraint on the column.
     */
    boolean existsByReferenceFacture(String referenceFacture);

    Page<DemandeFrais> findByAvocatId(Long avocatId, Pageable pageable);

    Page<DemandeFrais> findByAvocatIdAndStatutCircuit(Long avocatId, StatutCircuitFrais statut, Pageable pageable);

    Page<DemandeFrais> findByStatutCircuit(StatutCircuitFrais statut, Pageable pageable);

    /** CONF04: requests awaiting joint approval, for reminders. */
    List<DemandeFrais> findAllByStatutCircuit(StatutCircuitFrais statut);

    /** FR-REP-02 (dashboard): requests not yet settled (neither paid nor rejected). */
    @Query("SELECT COUNT(d) FROM DemandeFrais d "
            + "WHERE d.statutCircuit NOT IN :statuts AND d.dossier.categorie IN :categories")
    long compterNonSoldees(@Param("statuts") List<StatutCircuitFrais> statuts,
                           @Param("categories") List<CategorieDossier> categories);

    /** The requests counted by {@link #countByStatutCircuitNotIn}, oldest first (processing order). */
    @Query("SELECT d FROM DemandeFrais d "
            + "WHERE d.statutCircuit NOT IN :statuts AND d.dossier.categorie IN :categories "
            + "ORDER BY d.createdAt ASC")
    Page<DemandeFrais> listerNonSoldees(@Param("statuts") List<StatutCircuitFrais> statuts,
                                        @Param("categories") List<CategorieDossier> categories,
                                        Pageable pageable);

    /**
     * Archiving condition: a case's requests that are neither paid nor rejected. Returned rather than
     * counted so that the refusal can name them: the person archiving does not control the fee
     * workflow and needs to know whom to contact.
     */
    List<DemandeFrais> findByDossierIdAndStatutCircuitNotIn(Long dossierId, List<StatutCircuitFrais> statuts);

    /** FR-REP-01 (periodic report): requests filed within the period. */
    long countByCreatedAtBetween(LocalDateTime debut, LocalDateTime fin);

    /** FR-REP-01, by category. */
    @Query("SELECT COUNT(d) FROM DemandeFrais d "
            + "WHERE d.createdAt BETWEEN :debut AND :fin AND d.dossier.categorie IN :categories")
    long compterDeposeesEntre(@Param("debut") LocalDateTime debut,
                              @Param("fin") LocalDateTime fin,
                              @Param("categories") List<CategorieDossier> categories);

}
