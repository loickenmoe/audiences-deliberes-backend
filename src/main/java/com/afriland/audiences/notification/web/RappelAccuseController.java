package com.afriland.audiences.notification.web;

import com.afriland.audiences.notification.domain.NotificationAccuse.TypeAccuse;
import com.afriland.audiences.notification.service.AccuseNotificationService;
import com.afriland.audiences.notification.service.RappelsNotification;
import com.afriland.audiences.notification.web.dto.AccuseEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rappel du Notification Engine après traitement d'un message ({@code callbackUrl}, Guide V1 §7.9).
 * <p>
 * <b>C'est la voie qui rattache exactement un accusé à notre envoi</b> : la référence est dans
 * l'URL, puisque les topics de suivi ne la renvoient pas (Q-120).
 * <p>
 * La route est ouverte sans authentification — le Guide n'en décrit aucune pour ce rappel — mais
 * <b>la signature de l'URL est vérifiée</b> : sans elle, n'importe qui pourrait déclarer une
 * notification délivrée. Une signature invalide reçoit 404 plutôt que 403 : inutile de confirmer à un
 * appelant non autorisé que cette référence existe.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class RappelAccuseController {

    private final RappelsNotification rappels;
    private final AccuseNotificationService accuseNotificationService;

    @PostMapping("/notifications/accuses/rappel/{eventId}")
    public ResponseEntity<Void> rappel(@PathVariable String eventId,
                                       @RequestParam(name = RappelsNotification.PARAMETRE_SIGNATURE, required = false) String signature,
                                       @RequestBody(required = false) AccuseEngine accuse) {
        if (!rappels.signatureValide(eventId, signature)) {
            log.warn("Rappel refusé pour {} : signature absente ou invalide", eventId);
            return ResponseEntity.notFound().build();
        }
        accuseNotificationService.enregistrerRappel(eventId, accuse);
        return ResponseEntity.ok().build();
    }
}
