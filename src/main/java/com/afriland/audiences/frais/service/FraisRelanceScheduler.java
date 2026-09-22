package com.afriland.audiences.frais.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.frais.domain.DemandeFrais;
import com.afriland.audiences.frais.domain.EtapeValidationFrais;
import com.afriland.audiences.frais.domain.StatutCircuitFrais;
import com.afriland.audiences.frais.domain.ValidationFrais;
import com.afriland.audiences.frais.repository.DemandeFraisRepository;
import com.afriland.audiences.frais.repository.ValidationFraisRepository;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CONF04 (Q-83) : relance du DJ et/ou de la DJA quand une validation conjointe reste sans suite
 * au-delà de {@code DELAI_VALIDATION_CONJOINTE} jours — comptés depuis l'accord d'opportunité, puis
 * depuis la dernière relance. Seul celui qui n'a pas encore voté est relancé. Même principe que
 * {@code SuppressionRelanceScheduler} (Q-02).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FraisRelanceScheduler {

    private static final String CLE_DELAI = "DELAI_VALIDATION_CONJOINTE";

    private final DemandeFraisRepository demandeFraisRepository;
    private final ValidationFraisRepository validationFraisRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AlerteService alerteService;
    private final ConfigurationService configurationService;

    @Scheduled(cron = "${frais.relance-validation-conjointe.cron:0 0 * * * *}")
    @Transactional
    public void verifier() {
        List<DemandeFrais> enAttente = demandeFraisRepository.findAllByStatutCircuit(StatutCircuitFrais.OPPORTUNITE);
        if (enAttente.isEmpty()) {
            return;
        }
        long delaiJours;
        try {
            delaiJours = Long.parseLong(configurationService.getOrThrow(CLE_DELAI).getValeur().trim());
        } catch (NumberFormatException e) {
            log.warn("Relance de validation conjointe ignorée : {} n'est pas un nombre de jours.", CLE_DELAI);
            return;
        }
        LocalDateTime limite = LocalDateTime.now().minusDays(delaiJours);
        enAttente.forEach(demande -> relancerSiEchue(demande, delaiJours, limite));
    }

    private void relancerSiEchue(DemandeFrais demande, long delaiJours, LocalDateTime limite) {
        List<ValidationFrais> validations =
                validationFraisRepository.findByDemandeFraisIdOrderByDateValidationAscIdAsc(demande.getId());
        LocalDateTime reference = demande.getDateDerniereRelance() != null
                ? demande.getDateDerniereRelance()
                : validations.stream()
                        .filter(v -> v.getEtape() == EtapeValidationFrais.OPPORTUNITE)
                        .map(ValidationFrais::getDateValidation)
                        .findFirst()
                        .orElse(demande.getUpdatedAt());
        if (reference == null || reference.isAfter(limite)) {
            return;
        }

        List<Utilisateur> destinataires = new ArrayList<>();
        if (validations.stream().noneMatch(v -> v.getEtape() == EtapeValidationFrais.CONJOINTE_DJ)) {
            destinataires.addAll(utilisateurRepository.findByProfilCodeAndActifTrue("DJ"));
        }
        if (validations.stream().noneMatch(v -> v.getEtape() == EtapeValidationFrais.CONJOINTE_DJA)) {
            destinataires.addAll(utilisateurRepository.findByProfilCodeAndActifTrue("DJA"));
        }
        String declencheur = "Relance (" + delaiJours + " jours) : la demande de frais " + demande.getId()
                + " sur le dossier " + demande.getDossier().getReference()
                + " attend toujours votre validation conjointe (CONF04).";
        Long dossierId = demande.getDossier().getId();
        destinataires.forEach(u -> alerteService.declencher(TypeAlerte.VALIDATION_CONJOINTE_REQUISE, dossierId, u, declencheur));

        demande.setDateDerniereRelance(LocalDateTime.now());
        demandeFraisRepository.save(demande);
    }
}
