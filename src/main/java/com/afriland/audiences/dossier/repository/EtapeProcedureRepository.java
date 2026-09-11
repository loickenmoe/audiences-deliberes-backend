package com.afriland.audiences.dossier.repository;

import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.domain.TypeEtape;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EtapeProcedureRepository extends JpaRepository<EtapeProcedure, Long> {

    List<EtapeProcedure> findByDossierIdOrderByTypeAsc(Long dossierId);

    Optional<EtapeProcedure> findByDossierIdAndType(Long dossierId, TypeEtape type);

    boolean existsByDossierIdAndType(Long dossierId, TypeEtape type);

    /** FR-REP-02 (tableau de bord, M13) : dossiers portant au moins une étape hors {@code statutsFermes} — même définition qu'au Q-53. */
    @Query("SELECT COUNT(DISTINCT e.dossier.id) FROM EtapeProcedure e WHERE e.statutCycleVie NOT IN :statutsFermes")
    long countDistinctDossierIdByStatutCycleVieNotIn(@Param("statutsFermes") List<StatutCycleVie> statutsFermes);
}
