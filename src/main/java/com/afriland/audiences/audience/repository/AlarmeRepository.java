package com.afriland.audiences.audience.repository;

import com.afriland.audiences.audience.domain.Alarme;
import com.afriland.audiences.audience.domain.StatutAlarme;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlarmeRepository extends JpaRepository<Alarme, Long> {

    /** Due date reached (AlarmeEcheanceScheduler): active alarms to process. */
    List<Alarme> findByStatutAndDateEcheanceLessThanEqual(StatutAlarme statut, LocalDateTime maintenant);

    /** Alarms of a case, nearest due date first. */
    Page<Alarme> findByDossierIdOrderByDateEcheanceAsc(Long dossierId, Pageable pageable);
}
