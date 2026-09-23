package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.domain.NotificationAccuse.TypeAccuse;
import com.afriland.audiences.notification.web.dto.AccuseEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Réception des accusés sans sidecar, en lisant directement les topics de suivi. Monté seulement en
 * transport KAFKA ({@code notification.engine.transport.type}), par symétrie avec l'envoi.
 * <p>
 * Le groupe de consommation porte le code du service : deux instances de l'application se partagent
 * les messages au lieu de les traiter deux fois.
 * <p>
 * Un message illisible est journalisé puis acquitté : le rejouer indéfiniment bloquerait la lecture
 * des suivants, et un accusé perdu est moins grave qu'un suivi arrêté.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.engine.transport.type", havingValue = "KAFKA")
public class AccusesKafkaListener {

    private final AccuseNotificationService accuseNotificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "notification-sent", groupId = "${notification.engine.code-service:audience-deliberer}")
    public void surEnvoi(String charge) {
        traiter(charge, TypeAccuse.SENT);
    }

    @KafkaListener(topics = "notification-failed", groupId = "${notification.engine.code-service:audience-deliberer}")
    public void surEchec(String charge) {
        traiter(charge, TypeAccuse.FAILED);
    }

    private void traiter(String charge, TypeAccuse type) {
        try {
            accuseNotificationService.enregistrer(objectMapper.readValue(charge, AccuseEngine.class), type);
        } catch (Exception e) {
            log.error("Accusé {} illisible, ignoré : {}", type, e.getMessage());
        }
    }
}
