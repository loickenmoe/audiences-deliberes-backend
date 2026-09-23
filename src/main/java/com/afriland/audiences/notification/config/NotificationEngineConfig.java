package com.afriland.audiences.notification.config;

import com.afriland.audiences.notification.passerelle.PasserelleJournal;
import com.afriland.audiences.notification.passerelle.PasserelleNotificationEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Choix de la passerelle vers le Notification Engine.
 * <p>
 * Interrupteur désactivé (par défaut) : {@link PasserelleJournal}, rien n'est envoyé. Interrupteur
 * activé : le compte de service doit être renseigné, sinon le démarrage échoue avec un message
 * explicite plutôt que de laisser le moteur rejeter silencieusement chaque message.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(NotificationEngineProperties.class)
public class NotificationEngineConfig {

    @Bean
    public PasserelleNotificationEngine passerelleNotificationEngine(NotificationEngineProperties proprietes) {
        if (!proprietes.actif()) {
            log.info("Notification Engine désactivé : les notifications externes sont seulement journalisées");
            return new PasserelleJournal();
        }
        verifierCompteDeService(proprietes);
        verifierRedirection(proprietes);
        // Le transport réel (sidecar Dapr, Kafka direct en repli) arrive avec l'étape 8b.
        throw new IllegalStateException("notification.engine.actif=true, mais aucun transport réel n'est encore "
                + "disponible dans cette version : laisser NOTIFICATION_ENGINE_ACTIF=false");
    }

    /**
     * Hors production, la redirection (D10) doit être complète avant tout envoi : une cible manquante
     * ferait partir le message vers le destinataire réel, ce qu'elle existe précisément pour empêcher.
     */
    static void verifierRedirection(NotificationEngineProperties proprietes) {
        if (proprietes.redirection().actif() && !proprietes.redirection().complete()) {
            throw new IllegalStateException("Redirection active mais incomplète : renseigner "
                    + "NOTIFICATION_REDIRECTION_COURRIEL_INTERNE, _COURRIEL_PERSONNEL et _TELEPHONE, "
                    + "ou désactiver explicitement la redirection");
        }
    }

    static void verifierCompteDeService(NotificationEngineProperties proprietes) {
        if (proprietes.codeService() == null || proprietes.codeService().isBlank()) {
            throw new IllegalStateException("NOTIFICATION_ENGINE_CODE_SERVICE est obligatoire quand le Notification Engine est actif");
        }
        String secret = proprietes.jwtSecret();
        if (secret == null || secret.length() < NotificationEngineProperties.LONGUEUR_MIN_SECRET) {
            throw new IllegalStateException("NOTIFICATION_ENGINE_JWT_SECRET est obligatoire et doit faire au moins "
                    + NotificationEngineProperties.LONGUEUR_MIN_SECRET + " caractères (signature HS256)");
        }
    }
}
