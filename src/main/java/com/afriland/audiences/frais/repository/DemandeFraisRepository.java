package com.afriland.audiences.frais.repository;

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
     * Anti-doublon de facture (RG-INT-01, ERR-006) : unicité vérifiée sur l'ensemble des demandes,
     * quel que soit leur statut — y compris une demande déjà {@code REJETEE} (US 4.3, cf.
     * FUNCTIONAL_MAP.md §4 « Tests attendus »), conformément à la contrainte SQL {@code UNIQUE}
     * portée par la colonne.
     */
    boolean existsByReferenceFacture(String referenceFacture);

    Page<DemandeFrais> findByAvocatId(Long avocatId, Pageable pageable);

    Page<DemandeFrais> findByAvocatIdAndStatutCircuit(Long avocatId, StatutCircuitFrais statut, Pageable pageable);

    Page<DemandeFrais> findByStatutCircuit(StatutCircuitFrais statut, Pageable pageable);

    /** CONF04 (Q-83) : demandes en attente de validation conjointe, pour la relance. */
    List<DemandeFrais> findAllByStatutCircuit(StatutCircuitFrais statut);

    /** FR-REP-02 (tableau de bord, M13) : demandes non encore soldées (ni payées, ni rejetées). */
    long countByStatutCircuitNotIn(List<StatutCircuitFrais> statuts);

    /**
     * Les demandes que {@link #countByStatutCircuitNotIn} compte (Q-137) - la plus ancienne
     * d'abord, qui est l'ordre de traitement.
     */
    Page<DemandeFrais> findByStatutCircuitNotInOrderByCreatedAtAsc(List<StatutCircuitFrais> statuts,
                                                                   Pageable pageable);

    /**
     * Condition d archivage (2026-09-29) : les demandes d un dossier qui ne sont ni payées ni
     * rejetées. Rendues et non comptées : le refus les nomme, celui qui archive n ayant pas la main
     * sur le circuit des frais et devant savoir auprès de qui aller.
     */
    List<DemandeFrais> findByDossierIdAndStatutCircuitNotIn(Long dossierId, List<StatutCircuitFrais> statuts);

    /** FR-REP-01 (rapport périodique, M13) : demandes déposées dans la période. */
    long countByCreatedAtBetween(LocalDateTime debut, LocalDateTime fin);

}
