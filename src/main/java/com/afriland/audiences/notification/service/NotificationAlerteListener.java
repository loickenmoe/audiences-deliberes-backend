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
 * Prolonge chaque alerte applicative par ses notifications externes (NOTIFICATION_ENGINE_PLAN.md,
 * étape 6) : Outlook et/ou Teams pour un utilisateur interne, selon les réglages du DJ.
 * <p>
 * <b>Même transaction que l'action métier (D12).</b> {@code @EventListener} est synchrone : les lignes
 * de {@code notification_envoi} sont écrites avec l'alerte, et disparaissent avec elle si l'action
 * est annulée. Rien n'est envoyé ici ; l'envoi appartient au dispatcher (étape 8).
 * <p>
 * <b>Une notification ne doit jamais faire échouer l'action qui la provoque.</b> Le calcul (routage,
 * textes) est fait avant toute écriture ; s'il échoue, l'erreur est journalisée et l'alerte
 * applicative, elle, reste acquise. Les écritures ne peuvent échouer que sur une panne de la base,
 * qui aurait de toute façon emporté l'action métier.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationAlerteListener {

    /** Préfixe de la référence métier : une alerte donne au plus une ligne par canal. */
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
