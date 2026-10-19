package com.afriland.audiences.notification.passerelle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Fallback transport: the message is written directly to the Notification Engine's Kafka topic,
 * without a sidecar.
 * <p>
 * The sidecar envelope is reproduced ({@link EnveloppeCloudEvents}) because the engine consumes through
 * Dapr, which unwraps it. This avoids depending on a sidecar, which cannot be installed on workstations
 * (the image registry is filtered by the bank network). Raw sending remains available
 * ({@code cloud-events=false}).
 * <p>
 * The send is awaited, otherwise an unreachable broker would look like a success.
 */
@Slf4j
public class PasserelleKafka implements PasserelleNotificationEngine {

    public static final String NOM = "KAFKA";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final String topicDepot;
    private final long attenteSecondes;
    private final String pubsub;
    private final String source;
    private final boolean cloudEvents;

    public PasserelleKafka(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, String topic,
                           String topicDepot, long attenteSecondes, String pubsub, String source,
                           boolean cloudEvents) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.topicDepot = topicDepot;
        this.attenteSecondes = attenteSecondes;
        this.pubsub = pubsub;
        this.source = source;
        this.cloudEvents = cloudEvents;
    }

    @Override
    public boolean demanderDepot(DemandeDepotPieceJointe demande) {
        envoyer(topicDepot, demande.requestId(), serialiser(demande, topicDepot));
        log.debug("Dépôt de pièce jointe demandé pour {}", demande.requestId());
        return true;
    }

    @Override
    public String publier(MessageEngine message) {
        String charge;
        charge = serialiser(message, topic);
        envoyer(topic, message.eventId(), charge);
        log.debug("Message {} publié sur {} via Kafka", message.eventId(), topic);
        return NOM;
    }

    private String serialiser(Object contenu, String topicVise) {
        try {
            return objectMapper.writeValueAsString(cloudEvents
                    ? EnveloppeCloudEvents.autour(contenu, source, pubsub, topicVise)
                    : contenu);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Message illisible par le sérialiseur JSON", e);
        }
    }

    /** Awaited, otherwise an unreachable broker would look like a success. */
    private void envoyer(String topicVise, String cle, String charge) {
        try {
            // Keyed by eventId so messages of the same event keep their partition order.
            kafkaTemplate.send(topicVise, cle, charge).get(attenteSecondes, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publication Kafka interrompue", e);
        } catch (Exception e) {
            throw new IllegalStateException("Publication Kafka refusée (" + topicVise + ") : " + e.getMessage(), e);
        }
    }
}
