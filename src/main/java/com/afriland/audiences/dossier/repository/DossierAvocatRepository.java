package com.afriland.audiences.dossier.repository;

import com.afriland.audiences.dossier.domain.DossierAvocat;
import com.afriland.audiences.dossier.domain.DossierAvocatId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    interface ChargeAvocat {
        Long getAvocatId();

        Long getTotal();
    }
}
