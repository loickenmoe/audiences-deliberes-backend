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
 * Point d'entrée unique des notifications adressées à un prestataire (NOTIFICATION_ENGINE_PLAN.md,
 * étape 7 et décision D19), avocat comme autre prestataire.
 * <p>
 * <b>Ce que cela change.</b> Jusqu'ici, seul un avocat disposant d'un compte applicatif était
 * prévenu, et seulement dans l'application (Q-52) ; un notaire ou un huissier ne l'était jamais. Les
 * canaux externes ne demandent plus de compte : ils visent les coordonnées de la fiche.
 * <p>
 * <b>Appelé dans la transaction de l'action métier</b> (D12), au même endroit que l'alerte
 * applicative quand elle existe. Rien n'est envoyé ici : le dispatcher (étape 8) s'en charge.
 * Une notification ne doit jamais faire échouer l'action qui la provoque : le calcul est isolé, et
 * un événement déjà mis en file est ignoré plutôt que de heurter la contrainte d'unicité.
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
     * Met en file les canaux externes prévus pour cet événement.
     *
     * @param referenceEvenement référence métier stable et unique de l'événement, par exemple
     *                           {@code frais-12-rejet} : elle fonde la clé de déduplication
     */
    public void notifier(EvenementPrestataire evenement, Intervenant prestataire, Long dossierId,
                         String referenceEvenement) {
        notifier(evenement, prestataire, dossierId, referenceEvenement, null);
    }

    /**
     * @param pieceJointe document de la GED à joindre, ou {@code null}. Il n'est attaché qu'au
     *                    courriel (D5) : le Guide constate que WhatsApp et Teams la perdent en
     *                    silence, et que le SMS ne la supporte pas.
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
        // Q-87 : un prestataire chargé paresseusement est un proxy ; on ne teste jamais « instanceof » sans ceci.
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
            // Même événement rejoué (relance planifiée, avocat réaffecté) : on ne le remet pas en file.
            if (notificationEnvoiRepository.existsByEventId(envoi.getEventId())) {
                log.debug("Notification déjà en file, ignorée : {}", envoi.getEventId());
                continue;
            }
            envois.add(envoi);
        }
        return envois;
    }

    /**
     * Document de la GED à joindre à une notification (D5).
     *
     * @param chemin emplacement dans notre stockage, d'où le fichier sera lu pour être déposé chez
     *               le moteur
     */
    public record PieceJointe(Long documentId, String nom, String mime, String chemin) {
    }
}
