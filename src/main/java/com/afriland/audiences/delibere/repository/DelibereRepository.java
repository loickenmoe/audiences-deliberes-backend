package com.afriland.audiences.delibere.repository;

import com.afriland.audiences.delibere.domain.Delibere;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DelibereRepository extends JpaRepository<Delibere, Long> {

    /** Délibérés dont le délai de recours est échu et dont l'étape est toujours DELIBERE_VIDE (aucun recours exercé) — DelaiRecoursScheduler. */
    List<Delibere> findByDateEcheanceRecoursNotNullAndDateEcheanceRecoursLessThanEqualAndEtapeStatutCycleVie(
            LocalDateTime maintenant, StatutCycleVie statutCycleVie);

    /**
     * Indicateur de performance par avocat (RG-INT-01, `GET /repertoire/avocats`, M9) : nombre de
     * délibérés {@code FAVORABLE} rapporté au nombre total de délibérés rendus sur les dossiers où
     * l'avocat est affecté — seule mesure de « succès » disponible avant le module Décisions
     * définitives (M12, Q-52).
     */
    @Query("SELECT da.avocat.id AS avocatId, "
            + "SUM(CASE WHEN d.resultat = com.afriland.audiences.delibere.domain.ResultatDelibere.FAVORABLE THEN 1L ELSE 0L END) AS favorables, "
            + "COUNT(d) AS total "
            + "FROM Delibere d JOIN com.afriland.audiences.dossier.domain.DossierAvocat da ON da.dossier = d.dossier "
            + "GROUP BY da.avocat.id")
    List<PerformanceAvocat> calculerPerformanceParAvocat();

    interface PerformanceAvocat {
        Long getAvocatId();

        Long getFavorables();

        Long getTotal();
    }
}
