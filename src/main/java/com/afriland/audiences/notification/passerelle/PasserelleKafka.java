package com.afriland.audiences.notification.passerelle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Transport de repli : le message est déposé directement sur le topic Kafka du Notification Engine,
 * sans sidecar.
 * <p>
 * <b>Réserve à lever avant de s'en servir en premier choix.</b> Le moteur consomme ses topics par
 * Dapr, qui attend normalement l'enveloppe CloudEvents. Un message brut peut être accepté — Dapr sait
 * traiter une charge non conforme comme une donnée simple — ou ignoré en silence. Seuls les topics de
 * suivi (étape 9) le diront. Ce transport existe donc pour le cas où le sidecar ne serait pas
 * déployable, et il est choisi explicitement par configuration.
 * <p>
 * L'envoi est attendu : sans cela, un courtier injoignable passerait pour un succès, et la
 * notification serait marquée publiée sans être partie.
 */
@Slf4j
public class PasserelleKafka implements PasserelleNotificationEngine {

    public static final String NOM = "KAFKA";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final long attenteSecondes;

    public PasserelleKafka(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, String topic,
                           long attenteSecondes) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.attenteSecondes = attenteSecondes;
    }

    @Override
    public String publier(MessageEngine message) {
        String charge;
        try {
            charge = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Message illisible par le sérialiseur JSON", e);
        }
        try {
            // La clé est l'eventId : les messages d'un même événement gardent leur ordre de partition.
            kafkaTemplate.send(topic, message.eventId(), charge).get(attenteSecondes, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publication Kafka interrompue", e);
        } catch (Exception e) {
            throw new IllegalStateException("Publication Kafka refusée (" + topic + ") : " + e.getMessage(), e);
        }
        log.debug("Message {} publié sur {} via Kafka", message.eventId(), topic);
        return NOM;
    }
}
