package com.afriland.audiences.dossier.repository;

import com.afriland.audiences.referentiel.domain.CategorieDossier;

import com.afriland.audiences.dossier.domain.Dossier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.domain.TypeEtape;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EtapeProcedureRepository extends JpaRepository<EtapeProcedure, Long> {

    List<EtapeProcedure> findByDossierIdOrderByTypeAsc(Long dossierId);

    /** Steps of several cases in one query, avoiding one query per case when building a list. */
    List<EtapeProcedure> findByDossierIdInOrderByDossierIdAscTypeAsc(Collection<Long> dossierIds);

    Optional<EtapeProcedure> findByDossierIdAndType(Long dossierId, TypeEtape type);

    boolean existsByDossierIdAndType(Long dossierId, TypeEtape type);

    /**
     * Whether the case still has an active step. Used by the case-law guard: a decision is final
     * only when no step is still in progress.
     */
    boolean existsByDossierIdAndStatutCycleVieNotIn(Long dossierId, List<StatutCycleVie> statuts);

    /** FR-REP-02 (dashboard): cases with at least one step outside {@code statutsFermes}. */
    @Query("SELECT COUNT(DISTINCT e.dossier.id) FROM EtapeProcedure e "
            + "WHERE e.statutCycleVie NOT IN :statutsFermes AND e.dossier.categorie IN :categories")
    long countDistinctDossierIdByStatutCycleVieNotIn(@Param("statutsFermes") List<StatutCycleVie> statutsFermes,
                                                     @Param("categories") List<CategorieDossier> categories);

    /**
     * <b>Disposed cases</b>: cases with <b>no step still in progress</b>.
     * <p>Counted per case, not per closed step: a case with a closed Instance but an open Appeal is
     * not disposed.
     * <p>{@code statutsFermes} includes archiving: an archived step went through closure, and
     * excluding it would make the count drop as cases are archived.
     * <p>This is the <b>exact complement</b> of
     * {@link #countDistinctDossierIdByStatutCycleVieNotIn}.
     * <p>{@code not exists} subquery rather than a join, which would multiply rows per step.
     */
    @Query("""
            SELECT COUNT(DISTINCT e.dossier.id) FROM EtapeProcedure e
             WHERE e.dossier.categorie IN :categories
               AND NOT EXISTS (SELECT 1 FROM EtapeProcedure autre
                                WHERE autre.dossier = e.dossier
                                  AND autre.statutCycleVie NOT IN :statutsFermes)
            """)
    long countDossiersSansEtapeEnCours(@Param("statutsFermes") List<StatutCycleVie> statutsFermes,
                                       @Param("categories") List<CategorieDossier> categories);

    /**
     * The cases counted by {@link #countDistinctDossierIdByStatutCycleVieNotIn}, with <b>the same
     * predicate</b> so the dashboard detail matches its counter. {@code DISTINCT} per case, like
     * the count.
     */
    @Query("SELECT DISTINCT e.dossier FROM EtapeProcedure e "
            + "WHERE e.statutCycleVie NOT IN :statutsFermes AND e.dossier.categorie IN :categories")
    Page<Dossier> listerDossiersAvecEtapeEnCours(@Param("statutsFermes") List<StatutCycleVie> statutsFermes,
                                                 @Param("categories") List<CategorieDossier> categories,
                                                 Pageable pageable);

    /** The cases counted by {@link #countDossiersSansEtapeEnCours}, same subquery. */
    @Query("""
            SELECT DISTINCT e.dossier FROM EtapeProcedure e
             WHERE e.dossier.categorie IN :categories
               AND NOT EXISTS (SELECT 1 FROM EtapeProcedure autre
                                WHERE autre.dossier = e.dossier
                                  AND autre.statutCycleVie NOT IN :statutsFermes)
            """)
    Page<Dossier> listerDossiersSansEtapeEnCours(@Param("statutsFermes") List<StatutCycleVie> statutsFermes,
                                                 @Param("categories") List<CategorieDossier> categories,
                                                 Pageable pageable);
}
