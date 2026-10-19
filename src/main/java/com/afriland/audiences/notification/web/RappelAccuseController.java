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
 * Notification Engine callback after a message is processed ({@code callbackUrl}, Guide V1 §7.9).
 * This is the only way to link a receipt exactly to our send: the reference is in the URL, since the
 * tracking topics do not return it.
 * <p>
 * The route is unauthenticated (the Guide defines none) but <b>the URL signature is verified</b>,
 * otherwise anyone could mark a notification delivered. An invalid signature gets 404 rather than
 * 403 so as not to confirm the reference exists.
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
