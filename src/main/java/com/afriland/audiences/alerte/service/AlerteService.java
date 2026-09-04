package com.afriland.audiences.alerte.service;

import com.afriland.audiences.alerte.domain.Alerte;
import com.afriland.audiences.alerte.domain.CanalAlerte;
import com.afriland.audiences.alerte.domain.StatutAlerte;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.mapper.AlerteMapper;
import com.afriland.audiences.alerte.repository.AlerteRepository;
import com.afriland.audiences.alerte.web.dto.AlerteResponse;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Point d'entrée unique de déclenchement des alertes (RG-ALR-01/02/03), sur le même principe que
 * {@link com.afriland.audiences.common.audit.HistoriqueActionService} : les autres modules
 * métier (M6+) appelleront {@link #declencher} après l'événement concerné, sans que {@code
 * alerte} ait à connaître ces modules en retour (cf. Javadoc {@link
 * com.afriland.audiences.alerte.domain.Alerte}).
 * <p>
 * <b>Canal (Q-37, DECISIONS_AND_OPEN_QUESTIONS.md)</b> : seul {@link CanalAlerte#APPLICATIF} est
 * effectivement délivré en M5. {@code configuration_systeme.CANAUX_NOTIFICATION_DEFAUT} reste
 * consultable/modifiable via {@code ConfigurationService} mais Outlook/Teams ne sont pas câblés
 * — aucune infrastructure SMTP ni webhook Teams n'est fournie au projet ; l'implémenter sans elle
 * serait spéculatif. Chaque alerte est donc persistée avec {@code canal = APPLICATIF} et diffusée
 * en temps réel par WebSocket (Q-32) au destinataire s'il est connecté, avec {@code GET
 * /alertes/mes-notifications} comme repli garanti (l'enregistrement en base est inconditionnel).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlerteService {

    private static final String DESTINATION_UTILISATEUR = "/queue/alertes";

    private final AlerteRepository alerteRepository;
    private final AlerteMapper alerteMapper;
    private final CurrentUserService currentUserService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public Alerte declencher(TypeAlerte type, Long dossierId, Utilisateur destinataire, String declencheur) {
        Alerte alerte = Alerte.builder()
                .dossierId(dossierId)
                .destinataire(destinataire)
                .type(type)
                .declencheur(declencheur)
                .canal(CanalAlerte.APPLICATIF)
                .statut(StatutAlerte.DECLENCHEE)
                .build();
        alerte = alerteRepository.save(alerte);
        pousserNotification(destinataire, alerte);
        return alerte;
    }

    private void pousserNotification(Utilisateur destinataire, Alerte alerte) {
        try {
            messagingTemplate.convertAndSendToUser(destinataire.getKeycloakId(), DESTINATION_UTILISATEUR,
                    alerteMapper.toResponse(alerte));
        } catch (MessagingException e) {
            // Best-effort : le destinataire peut être hors ligne ou le broker temporairement
            // indisponible. L'alerte reste consultable via GET /alertes/mes-notifications (repli
            // garanti, Q-32) — aucune notification n'est jamais perdue, seul le push temps réel
            // peut manquer.
            log.warn("Échec de la diffusion WebSocket de l'alerte {} au destinataire {} : {}",
                    alerte.getId(), destinataire.getId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AlerteResponse> mesNotifications(StatutAlerte statut, Pageable pageable) {
        Utilisateur utilisateur = currentUserService.getCurrentUser();
        var page = statut == null
                ? alerteRepository.findByDestinataireId(utilisateur.getId(), pageable)
                : alerteRepository.findByDestinataireIdAndStatut(utilisateur.getId(), statut, pageable);
        return PageResponse.of(page, alerteMapper::toResponse);
    }

    /**
     * Anti-doublon pour un déclenchement planifié (ex. {@code CHARGE_MAX_AVOCAT}) : indique si
     * une alerte du même type, pour le même destinataire, portant le même marqueur, est déjà
     * {@code DECLENCHEE} — inutile de la redéclencher tant qu'elle n'a pas été traitée.
     */
    @Transactional(readOnly = true)
    public boolean estDejaSignalee(TypeAlerte type, Long destinataireId, String marqueur) {
        return alerteRepository.existsByTypeAndDestinataireIdAndStatutAndDeclencheurStartingWith(
                type, destinataireId, StatutAlerte.DECLENCHEE, marqueur);
    }

    /**
     * Anti-doublon pour un événement à signaler au plus une fois dans son existence (ex. rappel
     * J-N d'une audience, échéance d'une alarme) — contrairement à {@link #estDejaSignalee},
     * un traitement ultérieur de l'alerte n'autorise pas un nouveau déclenchement du même
     * événement : le marqueur doit encoder l'identité de l'événement lui-même (ex.
     * {@code "[audienceId:5;J-7] "}), pas seulement celle du sujet.
     */
    @Transactional(readOnly = true)
    public boolean aDejaEteDeclenchee(TypeAlerte type, Long destinataireId, String marqueur) {
        return alerteRepository.existsByTypeAndDestinataireIdAndDeclencheurStartingWith(type, destinataireId, marqueur);
    }

    @Transactional
    public AlerteResponse traiter(Long alerteId) {
        Alerte alerte = alerteRepository.findById(alerteId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Alerte introuvable : " + alerteId));
        Utilisateur utilisateur = currentUserService.getCurrentUser();
        if (!alerte.getDestinataire().getId().equals(utilisateur.getId())) {
            throw ApiException.forbidden(ErrorCodes.DROITS_INSUFFISANTS,
                    "Seul le destinataire de l'alerte peut la marquer comme traitée");
        }
        alerte.setStatut(StatutAlerte.TRAITEE);
        alerte.setDateTraitement(LocalDateTime.now());
        alerte = alerteRepository.save(alerte);
        return alerteMapper.toResponse(alerte);
    }
}
