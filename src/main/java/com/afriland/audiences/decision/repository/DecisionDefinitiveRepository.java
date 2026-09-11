package com.afriland.audiences.decision.repository;

import com.afriland.audiences.decision.domain.DecisionDefinitive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

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
}
