package com.afriland.audiences.delibere.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.delibere.domain.Delibere;
import com.afriland.audiences.delibere.repository.DelibereRepository;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.dossier.repository.DossierJuristeRepository;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.service.NotificationPrestataireService;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Appeal deadline tracking (FR-DEL-02, RG-DEL-02/04): at the deadline, if no appeal was lodged
 * (step still {@code DELIBERE_VIDE}), raises {@link TypeAlerte#DELAI_RECOURS} to the assigned legal
 * officers and moves the step to {@code CLOTURE}. Like the other schedulers, it lives in the
 * dependent module, never in {@code alerte} itself.
 * <p>
 * No history entry is recorded for this automatic transition, for lack of a "system" user
 * ({@code historique_action.utilisateur_id NOT NULL}); the alert itself provides traceability.
 */
@Component
@RequiredArgsConstructor
public class DelaiRecoursScheduler {

    private final DelibereRepository delibereRepository;
    private final EtapeProcedureRepository etapeProcedureRepository;
    private final DossierJuristeRepository dossierJuristeRepository;
    private final DossierAvocatRepository dossierAvocatRepository;
    private final AlerteService alerteService;
    private final NotificationPrestataireService notificationPrestataireService;

    @Scheduled(cron = "${delibere.recours.cron:0 0 6 * * *}")
    @Transactional
    public void verifier() {
        for (Delibere delibere : delibereRepository
                .findByDateEcheanceRecoursNotNullAndDateEcheanceRecoursLessThanEqualAndEtapeStatutCycleVie(
                        LocalDateTime.now(), StatutCycleVie.DELIBERE_VIDE)) {
            signalerEtCloturer(delibere);
        }
    }

    private void signalerEtCloturer(Delibere delibere) {
        Dossier dossier = delibere.getDossier();
        String marqueur = "[delibereId:" + delibere.getId() + "] ";
        String declencheur = marqueur + "Délai de recours expiré pour le délibéré du dossier "
                + dossier.getReference() + " — clôture automatique (RG-DEL-02).";
        for (var affectation : dossierJuristeRepository.findByDossierId(dossier.getId())) {
            Utilisateur juriste = affectation.getUtilisateur();
            if (!alerteService.aDejaEteDeclenchee(TypeAlerte.DELAI_RECOURS, juriste.getId(), marqueur)) {
                alerteService.declencher(TypeAlerte.DELAI_RECOURS, dossier.getId(), juriste, declencheur);
            }
        }
        // The case lawyer is also notified through external channels.
        for (var affectation : dossierAvocatRepository.findByDossierId(dossier.getId())) {
            notificationPrestataireService.notifier(EvenementPrestataire.DELAI_RECOURS, affectation.getAvocat(),
                    dossier.getId(), "delibere-" + delibere.getId() + "-recours");
        }

        EtapeProcedure etape = delibere.getEtape();
        if (etape != null && etape.getStatutCycleVie() == StatutCycleVie.DELIBERE_VIDE) {
            etape.setStatutCycleVie(StatutCycleVie.CLOTURE);
            etapeProcedureRepository.save(etape);
        }
    }
}
