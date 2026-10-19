package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.domain.NotificationAccuse.TypeAccuse;
import com.afriland.audiences.notification.web.dto.AccuseEngine;
import com.afriland.audiences.notification.web.dto.ReponseDepotPieceJointe;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Receives receipts without a sidecar by reading the tracking topics directly. Only active with the
 * KAFKA transport ({@code notification.engine.transport.type}).
 * <p>
 * The consumer group is the service code, so application instances share messages instead of
 * processing them twice.
 * <p>
 * An unreadable message is logged and acknowledged: replaying it forever would block the following ones.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.engine.transport.type", havingValue = "KAFKA")
public class AccusesKafkaListener {

    private final AccuseNotificationService accuseNotificationService;
    private final DepotPieceJointeService depotPieceJointeService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "notification-sent", groupId = "${notification.engine.code-service:audience-deliberer}")
    public void surEnvoi(String charge) {
        traiter(charge, TypeAccuse.SENT);
    }

    @KafkaListener(topics = "notification-failed", groupId = "${notification.engine.code-service:audience-deliberer}")
    public void surEchec(String charge) {
        traiter(charge, TypeAccuse.FAILED);
    }

    /** Attachment upload URL (Guide V1 §3 bis), awaited by the notification. */
    @KafkaListener(topics = "attachment-upload-response", groupId = "${notification.engine.code-service:audience-deliberer}")
    public void surReponseDepot(String charge) {
        try {
            depotPieceJointeService.traiterLaReponse(lire(charge, ReponseDepotPieceJointe.class));
        } catch (Exception e) {
            log.error("Réponse de dépôt illisible, ignorée : {}", e.getMessage());
        }
    }

    /**
     * The engine publishes through Dapr, so the payload is wrapped in a CloudEvent whose {@code data}
     * field is used. A message without envelope is read as is.
     */
    private <T> T lire(String charge, Class<T> type) throws com.fasterxml.jackson.core.JsonProcessingException {
        com.fasterxml.jackson.databind.JsonNode racine = objectMapper.readTree(charge);
        com.fasterxml.jackson.databind.JsonNode donnees = racine.has("data") ? racine.get("data") : racine;
        return objectMapper.treeToValue(donnees, type);
    }

    private void traiter(String charge, TypeAccuse type) {
        try {
            AccuseEngine accuse = lire(charge, AccuseEngine.class);
            // Logging other services' receipts at DEBUG distinguishes an empty topic from an active
            // topic with nothing for us.
            log.debug("Accusé {} lu sur le topic : service={}, canal={}", type, accuse.serviceId(), accuse.channel());
            accuseNotificationService.enregistrer(accuse, type);
        } catch (Exception e) {
            log.error("Accusé {} illisible, ignoré : {}", type, e.getMessage());
        }
    }
}
