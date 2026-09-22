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

    /** Échéance atteinte (AlarmeEcheanceScheduler) : alarmes encore actives à traiter. */
    List<Alarme> findByStatutAndDateEcheanceLessThanEqual(StatutAlarme statut, LocalDateTime maintenant);

    /** M15 : alarmes d'un dossier, échéance la plus proche d'abord (Q-68). */
    Page<Alarme> findByDossierIdOrderByDateEcheanceAsc(Long dossierId, Pageable pageable);
}
