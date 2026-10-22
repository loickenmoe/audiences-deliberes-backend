package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.config.NotificationEngineProperties;
import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.domain.TypeDestinataire;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Sandbox for non-production environments: every message goes to a test address or number, never to
 * the real recipient.
 * <p>
 * Redirecting rather than allow-listing, because development databases hold real lawyer contacts and
 * an allow-list would let through anything forgotten. The real recipient stays recorded in the queue,
 * so the substitution is verifiable.
 * <p>
 * The target depends on the channel <b>and</b> the audience: work address for internal users,
 * personal e-mail for providers, a phone number for SMS and WhatsApp.
 */
@Component
@RequiredArgsConstructor
public class RedirectionDestinataires {

    private final NotificationEngineProperties proprietes;

    /**
     * Redirection only matters when messages are really sent. With the engine off the logging gateway
     * sends nothing, and requiring test recipients would block the queue for no benefit.
     */
    public boolean active() {
        return proprietes.actif() && proprietes.redirection().actif();
    }

    /** Actual recipient to target; empty if redirection does not apply. */
    public Optional<String> cible(NotificationEnvoi envoi) {
        if (!active()) {
            return Optional.empty();
        }
        NotificationEngineProperties.Redirection redirection = proprietes.redirection();
        String cible = switch (envoi.getCanal()) {
            // The test number is entered like a contact record's, so it goes through the same formatting.
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
