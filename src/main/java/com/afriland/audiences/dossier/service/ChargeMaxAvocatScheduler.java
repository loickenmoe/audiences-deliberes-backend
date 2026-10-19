package com.afriland.audiences.dossier.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scheduled check of the {@code CHARGE_MAX_AVOCAT} threshold (RG-ALR-02, FR-ALR-01/US 7.1): alerts
 * active DJ users about any lawyer whose number of assigned cases exceeds the configured threshold.
 * Lives in {@code dossier} because it needs {@link DossierAvocatRepository}; {@code alerte} has no
 * dependency on business modules. Recipients are DJ users, since {@code PUT /alertes/seuils}
 * configures this threshold.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChargeMaxAvocatScheduler {

    private static final String CLE_SEUIL = "CHARGE_MAX_AVOCAT";
    private static final String PROFIL_DESTINATAIRE = "DJ";

    private final DossierAvocatRepository dossierAvocatRepository;
    private final AvocatRepository avocatRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ConfigurationService configurationService;
    private final AlerteService alerteService;

    @Scheduled(fixedDelayString = "${alerte.charge-max-avocat.interval-ms:3600000}")
    @Transactional
    public void verifier() {
        int seuil;
        try {
            seuil = Integer.parseInt(configurationService.getOrThrow(CLE_SEUIL).getValeur());
        } catch (NumberFormatException e) {
            log.error("Valeur de configuration {} non numérique, vérification CHARGE_MAX_AVOCAT ignorée", CLE_SEUIL);
            return;
        }

        List<Utilisateur> destinataires = utilisateurRepository.findByProfilCodeAndActifTrue(PROFIL_DESTINATAIRE);
        if (destinataires.isEmpty()) {
            return;
        }

        for (var charge : dossierAvocatRepository.compterDossiersParAvocat()) {
            if (charge.getTotal() <= seuil) {
                continue;
            }
            avocatRepository.findById(charge.getAvocatId()).ifPresent(avocat ->
                    signalerSiNecessaire(avocat, charge.getTotal(), seuil, destinataires));
        }
    }

    private void signalerSiNecessaire(Avocat avocat, long total, int seuil, List<Utilisateur> destinataires) {
        String marqueur = "[avocatId:" + avocat.getId() + "] ";
        String declencheur = marqueur + "L'avocat " + avocat.getNom() + " est affecté à " + total
                + " dossiers, au-delà du seuil configuré (" + seuil + ") — RG-ALR-02.";
        for (Utilisateur dj : destinataires) {
            if (alerteService.estDejaSignalee(TypeAlerte.CHARGE_MAX_AVOCAT, dj.getId(), marqueur)) {
                continue;
            }
            alerteService.declencher(TypeAlerte.CHARGE_MAX_AVOCAT, null, dj, declencheur);
        }
    }
}
