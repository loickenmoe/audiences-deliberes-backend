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
 * Échéance d'une alarme (Q-04) : génère une {@link TypeAlerte#ALARME} destinée à son créateur.
 * L'alarme reste {@code ACTIVE} après le déclenchement — seule une reprogrammation
 * (FR-AUD-04) la marque {@code TRAITEE} ; l'anti-doublon ({@link AlerteService#aDejaEteDeclenchee})
 * évite donc de la resignaler à chaque cycle tant qu'elle n'a pas été reprogrammée.
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
