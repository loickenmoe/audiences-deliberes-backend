package com.afriland.audiences.dossier.repository;

import com.afriland.audiences.dossier.domain.DossierAvocat;
import com.afriland.audiences.dossier.domain.DossierAvocatId;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DossierAvocatRepository extends JpaRepository<DossierAvocat, DossierAvocatId> {

    List<DossierAvocat> findByDossierId(Long dossierId);

    boolean existsByDossierIdAndAvocatId(Long dossierId, Long avocatId);

    void deleteByDossierId(Long dossierId);

    /** Nombre de dossiers affectés par avocat — alerte CHARGE_MAX_AVOCAT (RG-ALR-02, module alerte M5). */
    @Query("SELECT da.avocat.id AS avocatId, COUNT(da) AS total FROM DossierAvocat da GROUP BY da.avocat.id")
    List<ChargeAvocat> compterDossiersParAvocat();

    /**
     * Nombre de dossiers <b>actifs</b> par avocat (RG-INT-01, `GET /repertoire/avocats`, M9) — un
     * dossier est actif s'il porte au moins une étape hors {@code statutsFermes} (typiquement
     * {@code CLOTURE}/{@code ARCHIVE}). Distinct de {@link #compterDossiersParAvocat} (M5), qui
     * compte toute affectation sans notion d'activité.
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
