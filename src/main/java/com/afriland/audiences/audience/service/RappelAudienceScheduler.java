package com.afriland.audiences.audience.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.dossier.repository.DossierJuristeRepository;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.service.NotificationPrestataireService;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Scheduled hearing reminders at J-7, J-3 and J-1 (RG-AUD-03/FR-AUD-01). Calls
 * {@link AlerteService} one way, like
 * {@link com.afriland.audiences.dossier.service.ChargeMaxAvocatScheduler}.
 */
@Component
@RequiredArgsConstructor
public class RappelAudienceScheduler {

    private static final int[] DECALAGES_JOURS = {7, 3, 1};

    private final AudienceRepository audienceRepository;
    private final DossierJuristeRepository dossierJuristeRepository;
    private final DossierAvocatRepository dossierAvocatRepository;
    private final AlerteService alerteService;
    private final NotificationPrestataireService notificationPrestataireService;

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
        // The assigned lawyer gets the same reminder through external channels. The key holds the
        // hearing and the offset, so a queued J-7 reminder is never duplicated.
        for (var affectation : dossierAvocatRepository.findByDossierId(audience.getDossier().getId())) {
            notificationPrestataireService.notifier(EvenementPrestataire.RAPPEL_AUDIENCE, affectation.getAvocat(),
                    audience.getDossier().getId(), "audience-" + audience.getId() + "-J" + decalage);
        }
    }
}
