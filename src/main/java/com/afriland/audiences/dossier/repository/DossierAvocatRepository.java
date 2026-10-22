package com.afriland.audiences.dossier.repository;

import com.afriland.audiences.dossier.domain.DossierAvocat;
import com.afriland.audiences.dossier.domain.DossierAvocatId;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DossierAvocatRepository extends JpaRepository<DossierAvocat, DossierAvocatId> {

    List<DossierAvocat> findByDossierId(Long dossierId);

    /** Assignments of several cases in one query. */
    List<DossierAvocat> findByDossierIdIn(Collection<Long> dossierIds);

    boolean existsByDossierIdAndAvocatId(Long dossierId, Long avocatId);

    void deleteByDossierId(Long dossierId);

    /** Number of assigned cases per lawyer, for the CHARGE_MAX_AVOCAT alert (RG-ALR-02). */
    @Query("SELECT da.avocat.id AS avocatId, COUNT(da) AS total FROM DossierAvocat da GROUP BY da.avocat.id")
    List<ChargeAvocat> compterDossiersParAvocat();

    /**
     * Number of <b>active</b> cases per lawyer (RG-INT-01, {@code GET /repertoire/avocats}): a case
     * is active if it has at least one step outside {@code statutsFermes}. Unlike
     * {@link #compterDossiersParAvocat}, which counts every assignment.
     */
    @Query("SELECT da.avocat.id AS avocatId, COUNT(DISTINCT da.dossier.id) AS total FROM DossierAvocat da "
            + "WHERE EXISTS (SELECT 1 FROM EtapeProcedure e WHERE e.dossier = da.dossier AND e.statutCycleVie NOT IN :statutsFermes) "
            + "GROUP BY da.avocat.id")
    List<ChargeAvocat> compterDossiersActifsParAvocat(@Param("statutsFermes") List<StatutCycleVie> statutsFermes);

    interface ChargeAvocat {
        Long getAvocatId();

        Long getTotal();
    }
}
