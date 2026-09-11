package com.afriland.audiences.decision.repository;

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

    /**
     * FR-REP-02 (tableau de bord, M13) : montant total des provisions reprises (toutes périodes) —
     * requête portant sur le sous-type {@link com.afriland.audiences.decision.domain.Adjudication},
     * seul à porter {@code reliquat}/{@code repriseProvision}.
     */
    @Query("SELECT COALESCE(SUM(a.reliquat), 0) FROM Adjudication a WHERE a.repriseProvision = true")
    Double sommeProvisionsReprises();

    /** FR-REP-01 (rapport périodique, M13) : même indicateur, restreint aux adjudications décidées dans la période. */
    @Query("SELECT COALESCE(SUM(a.reliquat), 0) FROM Adjudication a WHERE a.repriseProvision = true AND a.dateDecision BETWEEN :debut AND :fin")
    Double sommeProvisionsReprisesPeriode(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    /** Q-88 : adjudications d'un dossier, la plus récente d'abord. */
    @Query("SELECT a FROM Adjudication a WHERE a.dossier.id = :dossierId ORDER BY a.dateDecision DESC, a.id DESC")
    List<Adjudication> adjudicationsDuDossier(@Param("dossierId") Long dossierId);

    /** Q-88 : condamnations pécuniaires d'un dossier, la plus récente d'abord. */
    @Query("SELECT c FROM CondamnationPecuniaire c WHERE c.dossier.id = :dossierId ORDER BY c.dateDecision DESC, c.id DESC")
    List<CondamnationPecuniaire> condamnationsDuDossier(@Param("dossierId") Long dossierId);
}
