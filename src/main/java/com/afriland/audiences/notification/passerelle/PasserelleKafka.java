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
 * <b>L'enveloppe du sidecar est reproduite</b> ({@link EnveloppeCloudEvents}) : le moteur consomme
 * ses topics par Dapr, qui déballe cette enveloppe avant de livrer le message. La produire nous-mêmes
 * donne au moteur exactement ce qu'il attend, sans dépendre d'un sidecar — lequel n'est pas
 * installable sur les postes, le registre d'images étant filtré par le réseau de la banque.
 * <p>
 * L'envoi brut, sans enveloppe, reste possible ({@code cloud-events=false}) pour départager les deux
 * hypothèses si le moteur restait muet.
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
    private final String pubsub;
    private final String source;
    private final boolean cloudEvents;

    public PasserelleKafka(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, String topic,
                           long attenteSecondes, String pubsub, String source, boolean cloudEvents) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.attenteSecondes = attenteSecondes;
        this.pubsub = pubsub;
        this.source = source;
        this.cloudEvents = cloudEvents;
    }

    @Override
    public String publier(MessageEngine message) {
        String charge;
        try {
            charge = objectMapper.writeValueAsString(cloudEvents
                    ? EnveloppeCloudEvents.autour(message, source, pubsub, topic)
                    : message);
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
