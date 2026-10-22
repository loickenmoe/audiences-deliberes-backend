package com.afriland.audiences.audience.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.audience.domain.Alarme;
import com.afriland.audiences.audience.domain.StatutAlarme;
import com.afriland.audiences.audience.repository.AlarmeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Alarm due date: raises a {@link TypeAlerte#ALARME} for its creator. The alarm stays
 * {@code ACTIVE} afterwards; it is only closed ({@code TRAITEE}) by rescheduling (FR-AUD-04) or
 * explicit handling, so the duplicate guard ({@link AlerteService#aDejaEteDeclenchee}) prevents
 * re-raising it every cycle.
 */
@Component
@RequiredArgsConstructor
public class AlarmeEcheanceScheduler {

    private final AlarmeRepository alarmeRepository;
    private final AlerteService alerteService;

    @Scheduled(fixedDelayString = "${audience.alarme-echeance.interval-ms:3600000}")
    @Transactional
    public void verifier() {
        for (Alarme alarme : alarmeRepository.findByStatutAndDateEcheanceLessThanEqual(StatutAlarme.ACTIVE, LocalDateTime.now())) {
            signaler(alarme);
        }
    }

    private void signaler(Alarme alarme) {
        String marqueur = "[alarmeId:" + alarme.getId() + "] ";
        if (alerteService.aDejaEteDeclenchee(TypeAlerte.ALARME, alarme.getCreatedBy().getId(), marqueur)) {
            return;
        }
        String declencheur = marqueur + "Échéance atteinte pour l'alarme « " + alarme.getObjet()
                + " » (dossier " + alarme.getDossier().getReference() + ").";
        alerteService.declencher(TypeAlerte.ALARME, alarme.getDossier().getId(), alarme.getCreatedBy(), declencheur);
    }
}
