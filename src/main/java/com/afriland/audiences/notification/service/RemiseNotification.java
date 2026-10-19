package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.config.NotificationEngineProperties;
import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.domain.StatutEnvoi;
import com.afriland.audiences.notification.passerelle.DemandeDepotPieceJointe;
import com.afriland.audiences.notification.passerelle.JetonEngine;
import com.afriland.audiences.notification.passerelle.MessageEngine;
import com.afriland.audiences.notification.passerelle.PasserelleNotificationEngine;
import com.afriland.audiences.notification.passerelle.PieceJointeEngine;
import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Delivers <b>one</b> notification to the Notification Engine in its own transaction, so one failure
 * does not affect the other rows of the pass.
 * <p>
 * Separate from {@link DispatcherNotification} on purpose: Spring only opens the transaction when the
 * call goes through the proxy of another bean.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RemiseNotification {

    /** Reason recorded when a notification is abandoned because it is too old. */
    public static final String MOTIF_PEREMPTION = "PERIMEE";

    private final NotificationEnvoiRepository notificationEnvoiRepository;
    private final PasserelleNotificationEngine passerelle;
    private final JetonEngine jetonEngine;
    private final RedirectionDestinataires redirection;
    private final RappelsNotification rappels;
    private final NotificationEngineProperties proprietes;

    @Transactional
    public void remettre(Long id) {
        NotificationEnvoi envoi = notificationEnvoiRepository.findById(id).orElse(null);
        if (envoi == null || envoi.getStatut() != StatutEnvoi.EN_ATTENTE) {
            return;
        }
        if (estPerimee(envoi)) {
            envoi.perimer(MOTIF_PEREMPTION);
            log.info("Notification {} périmée (créée le {}, âge maximal {}) : rien n'est envoyé",
                    envoi.getEventId(), envoi.getDateCreation(), proprietes.ageMax());
            return;
        }
        try {
            // Inside the try: incomplete redirection must fail the delivery, never send the message
            // to the real recipient.
            if (envoi.attendSaPieceJointe() && demanderLeDepot(envoi)) {
            return;
        }
        String destinataire = redirection.cible(envoi).orElseGet(envoi::getDestinataire);
            // The token is only signed when a message is really sent: the logging gateway does not
            // need it, and requiring it would block the queue while the secret is unset.
            String jeton = proprietes.actif() ? jetonEngine.signer() : null;
            PieceJointeEngine pieceJointe = envoi.getPieceJointeCle() == null ? null
                    : new PieceJointeEngine(envoi.getPieceJointeNom(), envoi.getPieceJointeMime(),
                            envoi.getPieceJointeCle());
            MessageEngine message = MessageEngine.de(jeton, proprietes.codeService(), proprietes.priorite(),
                    envoi.getCanal(), destinataire, envoi.getSujet(), envoi.getCorps(), envoi.getEventId(),
                    rappels.urlPour(envoi.getEventId()).orElse(null), pieceJointe);
            String transport = passerelle.publier(message);
            envoi.marquerPubliee(transport, destinataire);
        } catch (RuntimeException e) {
            LocalDateTime prochaine = prochaineTentative(envoi.getTentatives());
            envoi.echouerTentative(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), prochaine);
            log.warn("Remise en échec pour {} (tentative {} sur {}) : {}", envoi.getEventId(),
                    envoi.getTentatives(), proprietes.tentativesMax(), e.toString());
        }
    }

    /**
     * Requests an attachment upload URL from the engine (Guide V1 §3 bis). The response arrives on a
     * topic; the row waits until then.
     *
     * @return {@code true} if the row must wait; {@code false} if the notification can be sent now
     *         because the transport cannot request uploads
     */
    private boolean demanderLeDepot(NotificationEnvoi envoi) {
        try {
            DemandeDepotPieceJointe demande = new DemandeDepotPieceJointe(envoi.getEventId(),
                    envoi.getPieceJointeNom(), envoi.getPieceJointeMime(),
                    proprietes.actif() ? jetonEngine.signer() : null, proprietes.codeService());
            if (!passerelle.demanderDepot(demande)) {
                // Logging gateway: nothing is sent anyway, attachment included.
                envoi.pieceJointeAbandonnee("DEPOT_INDISPONIBLE");
                return false;
            }
            envoi.attendreLaPieceJointe(LocalDateTime.now().plus(proprietes.delaiDepot()));
            log.debug("Dépôt demandé pour {}, la notification attend sa clé", envoi.getEventId());
            return true;
        } catch (RuntimeException e) {
            log.warn("Demande de dépôt impossible pour {} : la notification partira sans sa pièce jointe",
                    envoi.getEventId(), e);
            envoi.pieceJointeAbandonnee("DEMANDE_DEPOT_ECHEC:" + e.getMessage());
            return false;
        }
    }

    private boolean estPerimee(NotificationEnvoi envoi) {
        return envoi.getDateCreation() != null
                && envoi.getDateCreation().plus(proprietes.ageMax()).isBefore(LocalDateTime.now());
    }

    /** Time of the next attempt, or {@code null} once attempts are exhausted. */
    LocalDateTime prochaineTentative(int tentativesDejaFaites) {
        List<Duration> delais = NotificationEngineProperties.DELAIS_TENTATIVES;
        if (tentativesDejaFaites + 1 >= proprietes.tentativesMax() || tentativesDejaFaites >= delais.size()) {
            return null;
        }
        return LocalDateTime.now().plus(delais.get(tentativesDejaFaites));
    }
}
