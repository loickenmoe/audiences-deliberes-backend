package com.afriland.audiences.notification.service;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.notification.config.NotificationEngineProperties;
import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.domain.StatutEnvoi;
import com.afriland.audiences.notification.passerelle.CanalEngine;
import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import com.afriland.audiences.notification.web.dto.SupervisionNotificationsResponse;
import com.afriland.audiences.notification.web.dto.SupervisionNotificationsResponse.EnvoiEnDefaut;
import com.afriland.audiences.notification.web.dto.SupervisionNotificationsResponse.LigneCanal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Monitoring of external notifications: what each channel produced over a time window, and the rows
 * needing review.
 * <p>
 * Counts come from the queue, not from receipts, since the queue knows what the application meant to
 * send. Receipts only match rows through the callback; otherwise a send stays "delivered to transport"
 * until the watcher marks it as without receipt. Both states are kept distinct.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupervisionNotificationService {

    /** Beyond this the list is unreadable; the table filters take over. */
    static final int MAX_ECHECS = 50;

    private final NotificationEnvoiRepository notificationEnvoiRepository;
    private final NotificationEngineProperties proprietes;

    @Transactional(readOnly = true)
    public SupervisionNotificationsResponse superviser(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut.isAfter(dateFin)) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "La date de début ne peut pas être postérieure à la date de fin");
        }
        LocalDateTime debut = dateDebut.atStartOfDay();
        LocalDateTime fin = dateFin.plusDays(1).atStartOfDay();

        Map<CanalEngine, Map<StatutEnvoi, Long>> comptes = new EnumMap<>(CanalEngine.class);
        for (Object[] ligne : notificationEnvoiRepository.compterParCanalEtStatut(debut, fin)) {
            comptes.computeIfAbsent((CanalEngine) ligne[0], canal -> new EnumMap<>(StatutEnvoi.class))
                    .put((StatutEnvoi) ligne[1], (Long) ligne[2]);
        }

        List<LigneCanal> canaux = new java.util.ArrayList<>();
        for (CanalEngine canal : CanalEngine.values()) {
            Map<StatutEnvoi, Long> parStatut = comptes.getOrDefault(canal, Map.of());
            canaux.add(new LigneCanal(canal.name(),
                    compte(parStatut, StatutEnvoi.EN_ATTENTE),
                    compte(parStatut, StatutEnvoi.PUBLIEE),
                    compte(parStatut, StatutEnvoi.ENVOYEE),
                    compte(parStatut, StatutEnvoi.ECHEC),
                    compte(parStatut, StatutEnvoi.SANS_RETOUR),
                    compte(parStatut, StatutEnvoi.NON_JOIGNABLE)));
        }

        List<EnvoiEnDefaut> echecs = notificationEnvoiRepository
                .trouverEnDefaut(debut, fin, PageRequest.of(0, MAX_ECHECS)).stream()
                .map(SupervisionNotificationService::versDefaut)
                .toList();

        return new SupervisionNotificationsResponse(dateDebut, dateFin, canaux, echecs, proprietes.actif(),
                proprietes.suiviAccuses());
    }

    /**
     * Requeues an abandoned send once its cause (missing contact, engine down) is fixed. The attempt
     * counter is reset since this is a human decision. Pending or confirmed sends are not requeued.
     */
    @Transactional
    public void relancer(Long id) {
        NotificationEnvoi envoi = notificationEnvoiRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Notification introuvable : " + id));
        if (envoi.getStatut() == StatutEnvoi.EN_ATTENTE || envoi.getStatut() == StatutEnvoi.ENVOYEE) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Cette notification n'est pas à relancer (statut " + envoi.getStatut() + ")");
        }
        if (envoi.getDestinataire() == null || envoi.getDestinataire().isBlank()) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Sans destinataire, la notification ne peut pas être relancée : corriger la fiche, "
                            + "l'événement suivant la reprendra");
        }
        envoi.relancer();
        log.info("Notification {} relancée manuellement", envoi.getEventId());
    }

    private static long compte(Map<StatutEnvoi, Long> parStatut, StatutEnvoi statut) {
        return parStatut.getOrDefault(statut, 0L);
    }

    private static EnvoiEnDefaut versDefaut(NotificationEnvoi envoi) {
        return new EnvoiEnDefaut(envoi.getId(), envoi.getEventId(), envoi.getTypeEvenement(),
                envoi.getCanal().name(), envoi.getStatut().name(), envoi.getDestinataire(),
                envoi.getDestinataireOrigine(), envoi.getDerniereErreur(), envoi.getTentatives(),
                envoi.getDateCreation() == null ? null : envoi.getDateCreation().toString());
    }
}
