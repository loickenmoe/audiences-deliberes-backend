package com.afriland.audiences.notification.web;

import com.afriland.audiences.notification.config.NotificationEngineProperties;
import com.afriland.audiences.notification.domain.NotificationAccuse.TypeAccuse;
import com.afriland.audiences.notification.service.AccuseNotificationService;
import com.afriland.audiences.notification.service.DepotPieceJointeService;
import com.afriland.audiences.notification.web.dto.AccuseEngine;
import com.afriland.audiences.notification.web.dto.ReponseDepotPieceJointe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Receives receipts through the Dapr sidecar (Guide V1 §8, recommended path).
 * <p>
 * Dapr discovers subscriptions via {@code GET /dapr/subscribe} at startup, so they are declared here
 * and follow the configured component and topics automatically.
 * <p>
 * <b>These routes are only reachable by the sidecar</b>, which shares the application's network stack
 * (see {@code docker-compose.yml}). They expose no application data.
 * <p>
 * A 200 acknowledges the message and stops redelivery, so 200 is returned even for receipts that do
 * not concern us.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AccusesDaprController {

    static final String ROUTE_SUCCES = "/notifications/accuses/envoyes";
    static final String ROUTE_ECHEC = "/notifications/accuses/echecs";
    static final String ROUTE_DEPOT = "/notifications/accuses/depots";

    private final AccuseNotificationService accuseNotificationService;
    private final DepotPieceJointeService depotPieceJointeService;
    private final NotificationEngineProperties proprietes;

    /** Subscriptions declared to the sidecar: the success and failure tracking topics. */
    @GetMapping("/dapr/subscribe")
    public List<Map<String, String>> abonnements() {
        String pubsub = proprietes.transport().pubsub();
        return List.of(
                Map.of("pubsubname", pubsub, "topic", "notification-sent", "route", ROUTE_SUCCES),
                Map.of("pubsubname", pubsub, "topic", "notification-failed", "route", ROUTE_ECHEC),
                Map.of("pubsubname", pubsub, "topic", "attachment-upload-response", "route", ROUTE_DEPOT));
    }

    @PostMapping(ROUTE_SUCCES)
    public ResponseEntity<Void> envoye(@RequestBody AccuseEngine accuse) {
        accuseNotificationService.enregistrer(accuse, TypeAccuse.SENT);
        return ResponseEntity.ok().build();
    }

    @PostMapping(ROUTE_ECHEC)
    public ResponseEntity<Void> echoue(@RequestBody AccuseEngine accuse) {
        accuseNotificationService.enregistrer(accuse, TypeAccuse.FAILED);
        return ResponseEntity.ok().build();
    }

    /** Attachment upload URL: the waiting notification resumes once the file is uploaded. */
    @PostMapping(ROUTE_DEPOT)
    public ResponseEntity<Void> depot(@RequestBody ReponseDepotPieceJointe reponse) {
        depotPieceJointeService.traiterLaReponse(reponse);
        return ResponseEntity.ok().build();
    }
}
