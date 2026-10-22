package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.domain.NotificationEnvoi.Origine;
import com.afriland.audiences.notification.domain.TypeDestinataire;
import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import com.afriland.audiences.notification.service.RedacteurNotification.ContenuNotification;
import com.afriland.audiences.notification.service.RedacteurNotification.Contexte;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Intervenant;
import com.afriland.audiences.referentiel.service.CompteAvocatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Single entry point for notifications to a provider, lawyer or other. External channels need no
 * application account: they target the contact details of the provider record.
 * <p>
 * <b>Called within the business action's transaction</b>, alongside the in-app alert when there is
 * one. Nothing is sent here; the dispatcher does it. A notification must never fail the triggering
 * action: computation is isolated, and an already queued event is skipped rather than hitting the
 * unique constraint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPrestataireService {

    private final RoutageNotification routage;
    private final RedacteurNotification redacteur;
    private final LiensApplication liens;
    private final ReferencesDossier referencesDossier;
    private final CompteAvocatService compteAvocatService;
    private final NotificationEnvoiRepository notificationEnvoiRepository;

    /**
     * Queues the external channels configured for this event.
     *
     * @param referenceEvenement stable, unique business reference of the event, e.g.
     *                           {@code frais-12-rejet}; it is the deduplication key
     */
    public void notifier(EvenementPrestataire evenement, Intervenant prestataire, Long dossierId,
                         String referenceEvenement) {
        notifier(evenement, prestataire, dossierId, referenceEvenement, null);
    }

    /**
     * @param pieceJointe document to attach, or {@code null}. Only attached to e-mail: WhatsApp and
     *                    Teams silently drop it and SMS does not support it.
     */
    public void notifier(EvenementPrestataire evenement, Intervenant prestataire, Long dossierId,
                         String referenceEvenement, PieceJointe pieceJointe) {
        List<NotificationEnvoi> envois;
        try {
            envois = preparer(evenement, prestataire, dossierId, referenceEvenement);
            if (pieceJointe != null) {
                envois.forEach(envoi -> envoi.joindre(pieceJointe.documentId(), pieceJointe.nom(),
                        pieceJointe.mime(), pieceJointe.chemin()));
            }
        } catch (RuntimeException e) {
            log.error("Notifications de l'événement {} ({}) non préparées pour le prestataire {}",
                    evenement, referenceEvenement, prestataire == null ? null : prestataire.getId(), e);
            return;
        }
        if (!envois.isEmpty()) {
            notificationEnvoiRepository.saveAll(envois);
        }
    }

    private List<NotificationEnvoi> preparer(EvenementPrestataire evenement, Intervenant prestataire, Long dossierId,
                                             String referenceEvenement) {
        if (prestataire == null) {
            return List.of();
        }
        // A lazily loaded provider is a proxy; never test "instanceof" without unproxying it first.
        Intervenant reel = (Intervenant) Hibernate.unproxy(prestataire);
        List<Route> routes = routage.pourPrestataire(evenement, new RoutageNotification.CoordonneesPrestataire(
                reel.getEmail(), reel.getTelephone(), reel.accepteNotificationsExternes()));
        if (routes.isEmpty()) {
            return List.of();
        }

        boolean avocat = reel instanceof Avocat;
        Contexte contexte = new Contexte(
                referencesDossier.reference(dossierId).orElse(null),
                avocat && compteAvocatService.utilisateurDe((Avocat) reel).isPresent() ? liens.portailAvocat() : null);
        Origine origine = new Origine(referenceEvenement + "-p" + reel.getId(), evenement.name(), dossierId, null,
                avocat ? TypeDestinataire.AVOCAT : TypeDestinataire.AUTRE_PRESTATAIRE, reel.getId());

        List<NotificationEnvoi> envois = new ArrayList<>();
        for (Route route : routes) {
            NotificationEnvoi envoi;
            if (route.joignable()) {
                ContenuNotification contenu = redacteur.pourPrestataire(evenement, route, contexte);
                envoi = NotificationEnvoi.aRemettre(origine, route.canal(), route.destinataire(),
                        contenu.sujet(), contenu.corps());
            } else {
                envoi = NotificationEnvoi.nonJoignable(origine, route.canal(), route.motif().name());
            }
            // Same event replayed (scheduled reminder, lawyer reassigned): not queued again.
            if (notificationEnvoiRepository.existsByEventId(envoi.getEventId())) {
                log.debug("Notification déjà en file, ignorée : {}", envoi.getEventId());
                continue;
            }
            envois.add(envoi);
        }
        return envois;
    }

    /**
     * Document to attach to a notification.
     *
     * @param chemin location in our storage, from which the file is read to upload it to the engine
     */
    public record PieceJointe(Long documentId, String nom, String mime, String chemin) {
    }
}
