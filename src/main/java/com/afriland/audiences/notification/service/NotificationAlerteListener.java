package com.afriland.audiences.notification.service;

import com.afriland.audiences.alerte.domain.AlerteDeclenchee;
import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.domain.NotificationEnvoi.Origine;
import com.afriland.audiences.notification.domain.TypeDestinataire;
import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import com.afriland.audiences.notification.service.RedacteurNotification.ContenuNotification;
import com.afriland.audiences.notification.service.RedacteurNotification.Contexte;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Extends each in-app alert with its external notifications: Outlook and/or Teams for an internal
 * user, according to the notification settings.
 * <p>
 * <b>Same transaction as the business action.</b> {@code @EventListener} is synchronous: the
 * {@code notification_envoi} rows are written with the alert and rolled back with it. Nothing is sent
 * here; sending belongs to the dispatcher.
 * <p>
 * <b>A notification must never fail the action that triggers it.</b> Routing and texts are computed
 * before any write; on failure the error is logged and the in-app alert is kept.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationAlerteListener {

    /** Business reference prefix: an alert yields at most one row per channel. */
    static final String PREFIXE_EVENEMENT = "alerte-";

    private final RoutageNotification routage;
    private final RedacteurNotification redacteur;
    private final LiensApplication liens;
    private final ReferencesDossier referencesDossier;
    private final NotificationEnvoiRepository notificationEnvoiRepository;

    @EventListener
    public void surAlerteDeclenchee(AlerteDeclenchee evenement) {
        List<NotificationEnvoi> envois;
        try {
            envois = preparer(evenement);
        } catch (RuntimeException e) {
            log.error("Notifications externes de l'alerte {} non préparées ; l'alerte applicative est conservée",
                    evenement.alerteId(), e);
            return;
        }
        if (!envois.isEmpty()) {
            notificationEnvoiRepository.saveAll(envois);
        }
    }

    List<NotificationEnvoi> preparer(AlerteDeclenchee evenement) {
        List<Route> routes = routage.pourUtilisateur(evenement.type(), evenement.destinataire());
        if (routes.isEmpty()) {
            return List.of();
        }
        Contexte contexte = new Contexte(
                referencesDossier.reference(evenement.dossierId()).orElse(null),
                liens.pourUtilisateur(evenement.dossierId()));
        Origine origine = new Origine(PREFIXE_EVENEMENT + evenement.alerteId(), evenement.type().name(),
                evenement.dossierId(), evenement.alerteId(), TypeDestinataire.UTILISATEUR,
                evenement.destinataire().getId());

        List<NotificationEnvoi> envois = new ArrayList<>();
        for (Route route : routes) {
            if (route.joignable()) {
                ContenuNotification contenu = redacteur.pourUtilisateur(evenement.type(), route, contexte);
                envois.add(NotificationEnvoi.aRemettre(origine, route.canal(), route.destinataire(),
                        contenu.sujet(), contenu.corps()));
            } else {
                envois.add(NotificationEnvoi.nonJoignable(origine, route.canal(), route.motif().name()));
            }
        }
        return envois;
    }
}
