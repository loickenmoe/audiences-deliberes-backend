package com.afriland.audiences.notification.passerelle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Transport documenté par le Guide V1 : le message est publié sur le sidecar Dapr, qui le dépose sur
 * le topic Kafka du Notification Engine.
 * <p>
 * <b>Pourquoi cette voie d'abord.</b> Le moteur consomme ses topics par Dapr, qui enveloppe chaque
 * message au format CloudEvents. Publier par Dapr, c'est produire exactement ce qu'il attend. Un
 * message rejeté ne donne aucun retour (Guide §4 bis) : mieux vaut suivre le chemin documenté que
 * deviner l'enveloppe.
 * <p>
 * Aucune bibliothèque Dapr n'est nécessaire : le sidecar expose une API HTTP locale.
 * Une réponse 2xx signifie que le sidecar a accepté le message, pas qu'il a été délivré — la
 * délivrance se lit sur les topics de suivi (étape 9).
 */
@Slf4j
public class PasserelleDapr implements PasserelleNotificationEngine {

    public static final String NOM = "DAPR";

    private final RestClient client;
    private final String pubsub;
    private final String topic;

    public PasserelleDapr(RestClient client, String pubsub, String topic) {
        this.client = client;
        this.pubsub = pubsub;
        this.topic = topic;
    }

    @Override
    public String publier(MessageEngine message) {
        try {
            client.post()
                    .uri("/v1.0/publish/{pubsub}/{topic}", pubsub, topic)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(message)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // Remontée telle quelle : le dispatcher la compte comme une tentative et replanifie.
            throw new IllegalStateException("Publication Dapr refusée (" + pubsub + "/" + topic + ") : " + e.getMessage(), e);
        }
        log.debug("Message {} publié sur {}/{} via Dapr", message.eventId(), pubsub, topic);
        return NOM;
    }
}
