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
 * Selects the Notification Engine gateway.
 * <p>
 * Disabled (default): {@link PasserelleJournal}, nothing is sent whatever the transport. Enabled: the
 * service account and redirection are checked at startup, then the chosen transport is wired (Dapr
 * sidecar as documented by the Guide, or direct Kafka as fallback). Failing at startup beats having
 * the engine silently reject every message.
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
                    transport.pubsub(), transport.topic(), transport.topicDepot());
            case KAFKA -> new PasserelleKafka(exigerKafka(kafkaTemplate),
                    objectMapper.getIfAvailable(ObjectMapper::new), transport.topic(), transport.topicDepot(),
                    transport.attenteSecondes(), new PasserelleKafka.Enveloppe(transport.pubsub(),
                            proprietes.codeService(), transport.cloudEvents()));
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
     * Outside production, redirection must be complete before any send: a missing target would let the
     * message reach the real recipient, which is exactly what redirection prevents.
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
