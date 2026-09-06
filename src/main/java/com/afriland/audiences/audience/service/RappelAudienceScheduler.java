package com.afriland.audiences.audience.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.dossier.repository.DossierJuristeRepository;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Rappels planifiés d'audience à J-7, J-3 et J-1 (RG-AUD-03/FR-AUD-01). Vit dans le module
 * {@code audience} — qui dépend de {@code dossier} pour les juristes affectés — et appelle
 * {@link AlerteService} en sens unique, sur le même principe que
 * {@link com.afriland.audiences.dossier.service.ChargeMaxAvocatScheduler} (Q-38).
 */
@Component
@RequiredArgsConstructor
public class RappelAudienceScheduler {

    private static final int[] DECALAGES_JOURS = {7, 3, 1};

    private final AudienceRepository audienceRepository;
    private final DossierJuristeRepository dossierJuristeRepository;
    private final AlerteService alerteService;

    @Scheduled(cron = "${audience.rappel.cron:0 0 6 * * *}")
    @Transactional
    public void verifier() {
        LocalDate aujourdHui = LocalDate.now();
        for (int decalage : DECALAGES_JOURS) {
            LocalDate dateCible = aujourdHui.plusDays(decalage);
            for (Audience audience : audienceRepository.findByStatutAndDatePlanifiee(StatutAudience.PLANIFIEE, dateCible)) {
                signaler(audience, decalage);
            }
        }
    }

    private void signaler(Audience audience, int decalage) {
        String marqueur = "[audienceId:" + audience.getId() + ";J-" + decalage + "] ";
        String declencheur = marqueur + "Audience du dossier " + audience.getDossier().getReference()
                + " prévue le " + audience.getDatePlanifiee() + " (J-" + decalage + ").";
        for (var affectation : dossierJuristeRepository.findByDossierId(audience.getDossier().getId())) {
            Utilisateur juriste = affectation.getUtilisateur();
            if (alerteService.aDejaEteDeclenchee(TypeAlerte.RAPPEL_AUDIENCE, juriste.getId(), marqueur)) {
                continue;
            }
            alerteService.declencher(TypeAlerte.RAPPEL_AUDIENCE, audience.getDossier().getId(), juriste, declencheur);
        }
    }
}
