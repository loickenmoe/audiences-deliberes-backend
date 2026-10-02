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
            // Remontée telle quelle : le dispatcher la compte comme une tentative et replanifie.
            throw new IllegalStateException("Publication Dapr refusée (" + pubsub + "/" + topicVise + ") : "
                    + e.getMessage(), e);
        }
    }
}
