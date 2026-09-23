package com.afriland.audiences.notification.config;

import com.afriland.audiences.notification.passerelle.PasserelleDapr;
import com.afriland.audiences.notification.passerelle.PasserelleJournal;
import com.afriland.audiences.notification.passerelle.PasserelleKafka;
import com.afriland.audiences.notification.passerelle.PasserelleNotificationEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;

/**
 * Choix de la passerelle vers le Notification Engine.
 * <p>
 * Interrupteur désactivé (par défaut) : {@link PasserelleJournal}, rien n'est envoyé, quel que soit le
 * transport configuré. Interrupteur activé : le compte de service et la redirection sont vérifiés au
 * démarrage, puis le transport demandé est monté — sidecar Dapr (voie documentée par le Guide) ou
 * Kafka direct (repli). Échouer au démarrage vaut mieux que laisser le moteur rejeter chaque message
 * en silence.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(NotificationEngineProperties.class)
public class NotificationEngineConfig {

    @Bean
    public PasserelleNotificationEngine passerelleNotificationEngine(
            NotificationEngineProperties proprietes,
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplate,
            ObjectProvider<ObjectMapper> objectMapper) {
        if (!proprietes.actif()) {
            log.info("Notification Engine désactivé : les notifications externes sont seulement journalisées");
            return new PasserelleJournal();
        }
        verifierCompteDeService(proprietes);
        verifierRedirection(proprietes);

        NotificationEngineProperties.Transport transport = proprietes.transport();
        log.info("Notification Engine actif — transport {}, topic {}", transport.type(), transport.topic());
        return switch (transport.type()) {
            case JOURNAL -> new PasserelleJournal();
            case DAPR -> new PasserelleDapr(RestClient.builder().baseUrl(transport.daprUrl()).build(),
                    transport.pubsub(), transport.topic());
            case KAFKA -> new PasserelleKafka(exigerKafka(kafkaTemplate),
                    objectMapper.getIfAvailable(ObjectMapper::new), transport.topic(),
                    transport.attenteSecondes());
        };
    }

    private static KafkaTemplate<String, String> exigerKafka(ObjectProvider<KafkaTemplate<String, String>> fournisseur) {
        KafkaTemplate<String, String> template = fournisseur.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("Transport KAFKA demandé mais aucun courtier configuré : "
                    + "renseigner spring.kafka.bootstrap-servers");
        }
        return template;
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
