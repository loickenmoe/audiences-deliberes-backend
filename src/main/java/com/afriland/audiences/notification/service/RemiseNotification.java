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
 * Remise d'<b>une</b> notification au Notification Engine, dans sa propre transaction : l'échec de
 * l'une n'emporte pas les autres lignes du même passage.
 * <p>
 * Classe distincte de {@link DispatcherNotification} à dessein : appelée depuis le planificateur
 * lui-même, la transaction ne s'ouvrirait pas — Spring ne l'intercepte que lorsque l'appel traverse
 * le proxy, c'est-à-dire un autre bean.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RemiseNotification {

    /** Motif inscrit dans la file quand une notification est abandonnée parce qu'elle a trop vieilli. */
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
            // Dans le try : une redirection incomplète doit faire échouer la remise, jamais laisser
            // partir le message vers le destinataire réel.
            if (envoi.attendSaPieceJointe() && demanderLeDepot(envoi)) {
            return;
        }
        String destinataire = redirection.cible(envoi).orElseGet(envoi::getDestinataire);
            // Le jeton n'est signé que si un message part vraiment : la passerelle journal n'en a pas
            // besoin, et l'exiger empêcherait de vider la file tant que le secret n'est pas renseigné.
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
     * Demande au moteur une adresse de dépôt pour la pièce jointe (Guide V1 §3 bis). La réponse
     * arrivera sur un topic : la ligne patiente jusque-là.
     *
     * @return {@code true} si la ligne doit attendre ; {@code false} si la notification peut partir
     *         tout de suite, faute de transport sachant demander un dépôt
     */
    private boolean demanderLeDepot(NotificationEnvoi envoi) {
        try {
            DemandeDepotPieceJointe demande = new DemandeDepotPieceJointe(envoi.getEventId(),
                    envoi.getPieceJointeNom(), envoi.getPieceJointeMime(),
                    proprietes.actif() ? jetonEngine.signer() : null, proprietes.codeService());
            if (!passerelle.demanderDepot(demande)) {
                // Passerelle journal : rien ne part de toute façon, la pièce jointe non plus.
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

    /** Échéance de la tentative suivante, ou {@code null} quand elles sont épuisées (D13). */
    LocalDateTime prochaineTentative(int tentativesDejaFaites) {
        List<Duration> delais = NotificationEngineProperties.DELAIS_TENTATIVES;
        if (tentativesDejaFaites + 1 >= proprietes.tentativesMax() || tentativesDejaFaites >= delais.size()) {
            return null;
        }
        return LocalDateTime.now().plus(delais.get(tentativesDejaFaites));
    }
}
