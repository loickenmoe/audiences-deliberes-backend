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
 * Suivi du délai de recours (FR-DEL-02, RG-DEL-02/04) : à l'échéance, si aucun recours n'a été
 * exercé (étape toujours {@code DELIBERE_VIDE}), déclenche {@link TypeAlerte#DELAI_RECOURS} vers
 * les juristes affectés puis bascule l'étape en {@code CLOTURE} — sur le même principe que
 * {@link com.afriland.audiences.dossier.service.ChargeMaxAvocatScheduler} (Q-38) et
 * {@link com.afriland.audiences.audience.service.RappelAudienceScheduler} (Q-39) : le
 * planificateur vit dans le module dépendant ({@code delibere}, qui dépend de {@code dossier} et
 * {@code alerte}), jamais dans {@code alerte} lui-même.
 * <p>
 * La « bascule décisions définitives » évoquée par FR-DEL-02 est différée au module M12 (pas
 * encore implémenté) — seule la transition d'étape et la notification sont réalisées ici (Q-43).
 * Aucun historique n'est enregistré pour cette transition automatique, faute d'utilisateur
 * "système" (contrainte {@code historique_action.utilisateur_id NOT NULL}) — même choix que les
 * planificateurs M5/M6, où seul le déclenchement de l'alerte fait foi de la traçabilité.
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
        // D19 : l'avocat du dossier est prévenu par ses canaux externes (Q-118).
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
