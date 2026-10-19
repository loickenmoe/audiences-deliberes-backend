package com.afriland.audiences.ged.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.ged.domain.DemandeSuppression;
import com.afriland.audiences.ged.domain.StatutSuppression;
import com.afriland.audiences.ged.repository.DemandeSuppressionRepository;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Automatic 72 h reminder for deletion requests still {@code EN_ATTENTE} (UC-GED-03 alt. 6-a).
 * Lives in the {@code ged} module (never in {@code alerte}), like the other schedulers.
 */
@Component
@RequiredArgsConstructor
public class SuppressionRelanceScheduler {

    private static final long DELAI_RELANCE_HEURES = 72;

    private final DemandeSuppressionRepository demandeSuppressionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AlerteService alerteService;

    @Scheduled(cron = "${ged.relance-suppression.cron:0 0 * * * *}")
    @Transactional
    public void verifier() {
        LocalDateTime seuil = LocalDateTime.now().minusHours(DELAI_RELANCE_HEURES);
        List<DemandeSuppression> aRelancer = new ArrayList<>();
        aRelancer.addAll(demandeSuppressionRepository
                .findByStatutAndDateDerniereRelanceIsNullAndDateDemandeLessThanEqual(StatutSuppression.EN_ATTENTE, seuil));
        aRelancer.addAll(demandeSuppressionRepository
                .findByStatutAndDateDerniereRelanceLessThanEqual(StatutSuppression.EN_ATTENTE, seuil));
        aRelancer.forEach(this::relancer);
    }

    private void relancer(DemandeSuppression demande) {
        String declencheur = "Relance (72 h) : demande de suppression toujours en attente pour le document « "
                + demande.getDocument().getNomFichier() + " » (dossier "
                + demande.getDocument().getDossier().getReference() + ").";
        List<Utilisateur> destinataires = utilisateurRepository.findByProfilCodeAndActifTrue("DJ");
        destinataires.addAll(utilisateurRepository.findByProfilCodeAndActifTrue("DJA"));
        destinataires.forEach(u -> alerteService.declencher(TypeAlerte.SUPPRESSION_DOCUMENT,
                demande.getDocument().getDossier().getId(), u, declencheur));
        demande.setDateDerniereRelance(LocalDateTime.now());
        demandeSuppressionRepository.save(demande);
    }
}
