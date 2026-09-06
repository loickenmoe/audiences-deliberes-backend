package com.afriland.audiences.delibere.repository;

import com.afriland.audiences.delibere.domain.Delibere;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DelibereRepository extends JpaRepository<Delibere, Long> {

    /** Délibérés dont le délai de recours est échu et dont l'étape est toujours DELIBERE_VIDE (aucun recours exercé) — DelaiRecoursScheduler. */
    List<Delibere> findByDateEcheanceRecoursNotNullAndDateEcheanceRecoursLessThanEqualAndEtapeStatutCycleVie(
            LocalDateTime maintenant, StatutCycleVie statutCycleVie);
}
