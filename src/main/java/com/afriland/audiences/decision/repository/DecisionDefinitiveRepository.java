package com.afriland.audiences.decision.repository;

import com.afriland.audiences.referentiel.domain.CategorieDossier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.afriland.audiences.decision.domain.Adjudication;
import com.afriland.audiences.decision.domain.CondamnationPecuniaire;
import com.afriland.audiences.decision.domain.DecisionDefinitive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DecisionDefinitiveRepository extends JpaRepository<DecisionDefinitive, Long> {

    /** Archiving precondition: a step cannot be archived without its final decision. */
    boolean existsByEtapeId(Long etapeId);

    /**
     * FR-REP-02 (dashboard): total reversed provisions, all periods. Queries the
     * {@link com.afriland.audiences.decision.domain.Adjudication} subtype, the only one with
     * {@code reliquat}/{@code repriseProvision}.
     */
    @Query("SELECT COALESCE(SUM(a.reliquat), 0) FROM Adjudication a "
            + "WHERE a.repriseProvision = true AND a.dossier.categorie IN :categories")
    Double sommeProvisionsReprises(@Param("categories") List<CategorieDossier> categories);

    /**
     * The adjudications summed by {@link #sommeProvisionsReprises}. Unlike count-based blocks, the
     * invariant is that the listed amounts add up to the displayed total, not the row count.
     */
    @Query("SELECT a FROM Adjudication a "
            + "WHERE a.repriseProvision = true AND a.dossier.categorie IN :categories "
            + "ORDER BY a.dateDecision DESC, a.id DESC")
    Page<Adjudication> listerProvisionsReprises(@Param("categories") List<CategorieDossier> categories,
                                                Pageable pageable);

    /** FR-REP-01 (periodic report): same indicator, restricted to adjudications decided in the period. */
    @Query("SELECT COALESCE(SUM(a.reliquat), 0) FROM Adjudication a "
            + "WHERE a.repriseProvision = true AND a.dossier.categorie IN :categories "
            + "AND a.dateDecision BETWEEN :debut AND :fin")
    Double sommeProvisionsReprisesPeriode(@Param("debut") LocalDate debut,
                                          @Param("fin") LocalDate fin,
                                          @Param("categories") List<CategorieDossier> categories);

    /** Adjudications of a case file, newest first. */
    @Query("SELECT a FROM Adjudication a WHERE a.dossier.id = :dossierId ORDER BY a.dateDecision DESC, a.id DESC")
    List<Adjudication> adjudicationsDuDossier(@Param("dossierId") Long dossierId);

    /** Monetary judgments of a case file, newest first. */
    @Query("SELECT c FROM CondamnationPecuniaire c WHERE c.dossier.id = :dossierId ORDER BY c.dateDecision DESC, c.id DESC")
    List<CondamnationPecuniaire> condamnationsDuDossier(@Param("dossierId") Long dossierId);
}
