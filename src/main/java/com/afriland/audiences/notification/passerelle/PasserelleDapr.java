package com.afriland.audiences.notification.passerelle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Transport documented by Guide V1: the message is published to the Dapr sidecar, which puts it on the
 * Notification Engine's Kafka topic. The engine consumes through Dapr (CloudEvents envelope), and a
 * rejected message gives no feedback (Guide §4 bis), so the documented path is preferred.
 * <p>
 * No Dapr library is needed: the sidecar exposes a local HTTP API. A 2xx means the sidecar accepted
 * the message, not that it was delivered; delivery is read from the tracking topics.
 */
@Slf4j
public class PasserelleDapr implements PasserelleNotificationEngine {

    public static final String NOM = "DAPR";

    private final RestClient client;
    private final String pubsub;
    private final String topic;
    private final String topicDepot;

    public PasserelleDapr(RestClient client, String pubsub, String topic, String topicDepot) {
        this.client = client;
        this.pubsub = pubsub;
        this.topic = topic;
        this.topicDepot = topicDepot;
    }

    @Override
    public boolean demanderDepot(DemandeDepotPieceJointe demande) {
        publierSur(topicDepot, demande);
        log.debug("Dépôt de pièce jointe demandé pour {}", demande.requestId());
        return true;
    }

    @Override
    public String publier(MessageEngine message) {
        publierSur(topic, message);
        log.debug("Message {} publié sur {}/{} via Dapr", message.eventId(), pubsub, topic);
        return NOM;
    }

    private void publierSur(String topicVise, Object contenu) {
        try {
            client.post()
                    .uri("/v1.0/publish/{pubsub}/{topic}", pubsub, topicVise)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(contenu)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // Rethrown as is: the dispatcher counts it as an attempt and reschedules.
            throw new IllegalStateException("Publication Dapr refusée (" + pubsub + "/" + topicVise + ") : "
                    + e.getMessage(), e);
        }
    }
}
