package com.afriland.audiences.alerte.service;

import com.afriland.audiences.alerte.domain.Alerte;
import com.afriland.audiences.alerte.domain.AlerteDeclenchee;
import com.afriland.audiences.alerte.domain.CanalAlerte;
import com.afriland.audiences.alerte.domain.StatutAlerte;
import com.afriland.audiences.alerte.domain.StatutDiffusion;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.mapper.AlerteMapper;
import com.afriland.audiences.alerte.repository.AlerteRepository;
import com.afriland.audiences.alerte.web.dto.AlerteResponse;
import com.afriland.audiences.alerte.web.dto.StatistiquesDiffusionResponse;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Single entry point for raising alerts (RG-ALR-01/02/03). Business modules call
 * {@link #declencher}; this module does not depend on them in return.
 * <p>
 * <b>In-app channel</b>: each alert is persisted with {@code canal = APPLICATIF} and pushed over
 * WebSocket if the recipient is connected; {@code GET /alertes/mes-notifications} is the guaranteed
 * fallback.
 * <p>
 * <b>External channels</b>: Outlook and Teams go through the bank's Notification Engine. This
 * service only publishes {@link AlerteDeclenchee} within the business transaction; the notification
 * module queues the sends.
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
    /** Live STOMP sessions, keyed by the token {@code sub}; see {@code pousserNotification}. */
    private final SimpUserRegistry simpUserRegistry;
    /** Extensions of the alert outside the in-app channel (external notifications). */
    private final ApplicationEventPublisher evenements;

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
        // The delivery outcome is set on an already managed entity and persisted at commit; no
        // second save() is needed. Persistence of the alert never depends on delivery success.
        pousserNotification(destinataire, alerte);
        evenements.publishEvent(new AlerteDeclenchee(alerte.getId(), type, dossierId, destinataire));
        return alerte;
    }

    /**
     * Pushes the alert in real time and <b>records the outcome</b> (NF-ALR-02).
     * <p>{@code convertAndSendToUser} silently succeeds when nobody is subscribed, so a try/catch
     * alone would always report 100 % success. {@link SimpUserRegistry} knows the live STOMP
     * sessions, keyed by the Keycloak token {@code sub} (= {@code Utilisateur.keycloakId}).
     * <p>The alert is persisted before any push, so {@link StatutDiffusion#DIFFEREE} is a later
     * delivery, not a loss.
     */
    private void pousserNotification(Utilisateur destinataire, Alerte alerte) {
        boolean connecte = simpUserRegistry.getUser(destinataire.getKeycloakId()) != null;
        alerte.setDateDiffusion(LocalDateTime.now());
        try {
            messagingTemplate.convertAndSendToUser(destinataire.getKeycloakId(), DESTINATION_UTILISATEUR,
                    alerteMapper.toResponse(alerte));
            alerte.setStatutDiffusion(connecte ? StatutDiffusion.DIFFUSEE : StatutDiffusion.DIFFEREE);
        } catch (MessagingException e) {
            // Broker unavailable or transport broken: the only case NF-ALR-02 counts as a loss. The
            // alert remains available via GET /alertes/mes-notifications.
            alerte.setStatutDiffusion(StatutDiffusion.ECHEC);
            log.warn("Échec de la diffusion WebSocket de l'alerte {} au destinataire {} : {}",
                    alerte.getId(), destinataire.getId(), e.getMessage());
        }
    }

    /**
     * In-app channel delivery rates over a window (NF-ALR-02).
     * <p>Two distinct rates: the <i>delivery</i> rate (what NF-ALR-02 requires, only failures count
     * against it) and the <i>real-time</i> rate (how often recipients are connected; a low value is
     * not a system fault).
     * <p>Alerts created before V16 have no recorded outcome and are excluded.
     */
    @Transactional(readOnly = true)
    public StatistiquesDiffusionResponse statistiquesDiffusion(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut.isAfter(dateFin)) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "La date de début ne peut pas être postérieure à la date de fin");
        }
        LocalDateTime debut = dateDebut.atStartOfDay();
        LocalDateTime fin = dateFin.plusDays(1).atStartOfDay();

        long diffusees = alerteRepository.compterParDiffusion(StatutDiffusion.DIFFUSEE, debut, fin);
        long differees = alerteRepository.compterParDiffusion(StatutDiffusion.DIFFEREE, debut, fin);
        long echecs = alerteRepository.compterParDiffusion(StatutDiffusion.ECHEC, debut, fin);
        long nonTraitees = alerteRepository.compterNonTraitees(debut, fin);

        return StatistiquesDiffusionResponse.calculer(dateDebut, dateFin, diffusees, differees, echecs, nonTraitees);
    }

    @Transactional(readOnly = true)
    public PageResponse<AlerteResponse> mesNotifications(StatutAlerte statut, TypeAlerte type,
                                                            Long dossierId, Pageable pageable) {
        Utilisateur utilisateur = currentUserService.getCurrentUser();
        var page = alerteRepository.rechercher(utilisateur.getId(), statut, type, dossierId, pageable);
        return PageResponse.of(page, alerteMapper::toResponse);
    }

    /**
     * Duplicate guard for a scheduled alert (e.g. {@code CHARGE_MAX_AVOCAT}): true if an alert with
     * the same type, recipient and marker is still {@code DECLENCHEE}.
     */
    @Transactional(readOnly = true)
    public boolean estDejaSignalee(TypeAlerte type, Long destinataireId, String marqueur) {
        return alerteRepository.existsByTypeAndDestinataireIdAndStatutAndDeclencheurStartingWith(
                type, destinataireId, StatutAlerte.DECLENCHEE, marqueur);
    }

    /**
     * Duplicate guard for an event reported at most once in its lifetime (e.g. J-N hearing
     * reminder, alarm due date). Unlike {@link #estDejaSignalee}, handling the alert does not allow
     * a new one, so the marker must encode the event identity itself (e.g.
     * {@code "[audienceId:5;J-7] "}).
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
