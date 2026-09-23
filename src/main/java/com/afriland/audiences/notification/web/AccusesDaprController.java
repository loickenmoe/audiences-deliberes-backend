package com.afriland.audiences.notification.web;

import com.afriland.audiences.notification.config.NotificationEngineProperties;
import com.afriland.audiences.notification.domain.NotificationAccuse.TypeAccuse;
import com.afriland.audiences.notification.service.AccuseNotificationService;
import com.afriland.audiences.notification.web.dto.AccuseEngine;
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
 * Réception des accusés par le sidecar Dapr (Guide V1 §8, voie recommandée).
 * <p>
 * Dapr découvre les abonnements en appelant {@code GET /dapr/subscribe} au démarrage, puis livre
 * chaque message sur la route déclarée. Les abonnements sont donc annoncés ici plutôt que dans un
 * fichier : ils suivent automatiquement le composant et les topics configurés.
 * <p>
 * <b>Ces routes ne sont ouvertes qu'au sidecar</b>, qui tourne sur la même pile réseau que
 * l'application (cf. {@code docker-compose.yml}). Elles ne portent aucune donnée de l'application et
 * n'en renvoient aucune : elles enregistrent ce que le moteur dit de ses propres envois.
 * <p>
 * Une réponse 200 vaut acquittement : Dapr ne redélivrera pas. On répond donc 200 même lorsque
 * l'accusé ne nous concerne pas — il a bien été traité, et le redemander ne changerait rien.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AccusesDaprController {

    static final String ROUTE_SUCCES = "/notifications/accuses/envoyes";
    static final String ROUTE_ECHEC = "/notifications/accuses/echecs";

    private final AccuseNotificationService accuseNotificationService;
    private final NotificationEngineProperties proprietes;

    /** Abonnements annoncés au sidecar : le topic de suivi des succès et celui des échecs. */
    @GetMapping("/dapr/subscribe")
    public List<Map<String, String>> abonnements() {
        String pubsub = proprietes.transport().pubsub();
        return List.of(
                Map.of("pubsubname", pubsub, "topic", "notification-sent", "route", ROUTE_SUCCES),
                Map.of("pubsubname", pubsub, "topic", "notification-failed", "route", ROUTE_ECHEC));
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
}
