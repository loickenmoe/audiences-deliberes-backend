package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.config.NotificationEngineProperties;
import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.domain.TypeDestinataire;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Bac à sable des environnements de travail (D10) : hors production, chaque message part vers une
 * adresse ou un numéro de test, jamais vers le destinataire réel.
 * <p>
 * <b>Pourquoi rediriger plutôt que filtrer.</b> Les bases de développement contiennent de vraies
 * coordonnées d'avocats. Une liste d'adresses autorisées laisserait passer tout ce qu'on oublierait
 * d'y mettre ; la redirection, elle, ne laisse rien passer et permet de vérifier tous les scénarios.
 * Le destinataire réel reste enregistré dans la file, ce qui rend la substitution vérifiable.
 * <p>
 * La cible dépend du canal <b>et</b> du public : une adresse professionnelle pour un interne, un
 * courriel personnel pour un prestataire, un numéro pour le SMS et WhatsApp.
 */
@Component
@RequiredArgsConstructor
public class RedirectionDestinataires {

    private final NotificationEngineProperties proprietes;

    /**
     * La redirection n'a de sens que lorsque des messages partent réellement. Moteur coupé, la
     * passerelle journal n'envoie rien : exiger des destinataires de test bloquerait la file sans
     * rien protéger, et le stock ne se viderait jamais.
     */
    public boolean active() {
        return proprietes.actif() && proprietes.redirection().actif();
    }

    /** Destinataire à viser réellement ; vide si la redirection ne s'applique pas. */
    public Optional<String> cible(NotificationEnvoi envoi) {
        if (!active()) {
            return Optional.empty();
        }
        NotificationEngineProperties.Redirection redirection = proprietes.redirection();
        String cible = switch (envoi.getCanal()) {
            // Le numéro de test est saisi comme celui d'une fiche : il passe par la même mise en forme.
            case SMS, WHATSAPP -> Coordonnees.telephone(redirection.telephone()).orElse(null);
            case TEAMS -> redirection.courrielInterne();
            case EMAIL -> envoi.getDestinataireType() == TypeDestinataire.UTILISATEUR
                    ? redirection.courrielInterne()
                    : redirection.courrielPersonnel();
        };
        if (cible == null || cible.isBlank()) {
            throw new IllegalStateException("Redirection active mais sans destinataire de test pour le canal "
                    + envoi.getCanal() + " : renseigner NOTIFICATION_REDIRECTION_*");
        }
        return Optional.of(cible);
    }
}
