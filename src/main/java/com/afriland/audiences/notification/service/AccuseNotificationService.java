package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.config.NotificationEngineProperties;
import com.afriland.audiences.notification.domain.NotificationAccuse;
import com.afriland.audiences.notification.domain.NotificationAccuse.TypeAccuse;
import com.afriland.audiences.notification.repository.NotificationAccuseRepository;
import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import com.afriland.audiences.notification.web.dto.AccuseEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Records Notification Engine receipts ({@code notification-sent} and {@code notification-failed}),
 * whatever path they arrive through.
 * <ul>
 *   <li><b>Service filtering</b>: the topics carry receipts of all bank services; only ours are kept,
 *       by {@code serviceId}.</li>
 *   <li><b>At-least-once delivery</b>: a duplicate receipt is silently ignored thanks to the
 *       (notificationId, type) uniqueness.</li>
 * </ul>
 * A receipt does not identify our queue row, so it is stored unlinked and used for per-channel and
 * per-provider metrics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccuseNotificationService {

    private final NotificationAccuseRepository notificationAccuseRepository;
    private final NotificationEnvoiRepository notificationEnvoiRepository;
    private final NotificationEngineProperties proprietes;

    /**
     * @return {@code true} if the receipt concerned us and was recorded
     */
    @Transactional
    public boolean enregistrer(AccuseEngine accuse, TypeAccuse type) {
        if (accuse == null || accuse.notificationId() == null || accuse.notificationId().isBlank()) {
            log.warn("Accusé {} sans identifiant de notification : ignoré", type);
            return false;
        }
        if (!nousConcerne(accuse)) {
            return false;
        }
        if (notificationAccuseRepository.existsByNotificationIdAndType(accuse.notificationId(), type)) {
            // At-least-once delivery: a duplicate is expected, not an anomaly.
            log.debug("Accusé {} déjà enregistré pour {}", type, accuse.notificationId());
            return false;
        }
        NotificationAccuse enregistre = notificationAccuseRepository.save(NotificationAccuse.de(
                accuse.notificationId(), type, accuse.serviceId(), accuse.channel(), accuse.provider(),
                accuse.errorMessage(), accuse.permanent(), accuse.instant()));

        if (type == TypeAccuse.FAILED) {
            log.warn("Le moteur signale un échec sur le canal {} (fournisseur {}) : {}",
                    accuse.channel(), accuse.provider(), accuse.errorMessage());
        }
        rattacher(enregistre, accuse);
        return true;
    }

    /**
     * Links the receipt to its queue row <b>if the engine provides the means</b>. It currently does not
     * return the {@code eventId}, so this takes effect only once receipts carry it.
     */
    private void rattacher(NotificationAccuse accuseEnregistre, AccuseEngine accuse) {
        if (accuse.eventId() == null || accuse.eventId().isBlank()) {
            return;
        }
        notificationEnvoiRepository.findByEventId(accuse.eventId()).ifPresent(envoi -> {
            accuseEnregistre.rattacherA(envoi.getId());
            if (accuseEnregistre.getType() == TypeAccuse.SENT) {
                envoi.marquerEnvoyee(accuse.provider(), accuse.instant());
            } else {
                envoi.marquerEchecDuMoteur(accuse.errorMessage(), accuse.instant());
            }
        });
    }

    /**
     * Callback received on our {@code callbackUrl}: the reference is known, so the link is exact.
     * <p>
     * The outcome is read from the callback body when present; the Guide does not define its shape,
     * so the absence of an error counts as success.
     */
    @Transactional
    public void enregistrerRappel(String eventId, AccuseEngine accuse) {
        boolean echec = accuse != null
                && (accuse.errorMessage() != null || Boolean.TRUE.equals(accuse.permanent()) || accuse.failedAt() != null);
        TypeAccuse type = echec ? TypeAccuse.FAILED : TypeAccuse.SENT;

        notificationEnvoiRepository.findByEventId(eventId).ifPresentOrElse(envoi -> {
            if (echec) {
                envoi.marquerEchecDuMoteur(accuse.errorMessage(), accuse.instant());
            } else {
                envoi.marquerEnvoyee(accuse == null ? "CALLBACK" : accuse.provider(),
                        accuse == null ? LocalDateTime.now() : accuse.instant());
            }
            enregistrerSiNouveau(accuse, type, eventId, envoi.getId());
        }, () -> log.warn("Rappel {} reçu pour une référence inconnue : {}", type, eventId));
    }

    private void enregistrerSiNouveau(AccuseEngine accuse, TypeAccuse type, String eventId, Long envoiId) {
        // Without an engine id, the reference acts as the key so the callback stays idempotent.
        String notificationId = accuse != null && accuse.notificationId() != null ? accuse.notificationId() : eventId;
        if (notificationAccuseRepository.existsByNotificationIdAndType(notificationId, type)) {
            return;
        }
        NotificationAccuse enregistre = notificationAccuseRepository.save(NotificationAccuse.de(notificationId, type,
                proprietes.codeService(), accuse == null ? null : accuse.channel(), accuse == null ? null : accuse.provider(),
                accuse == null ? null : accuse.errorMessage(), accuse == null ? null : accuse.permanent(),
                accuse == null ? LocalDateTime.now() : accuse.instant()));
        enregistre.rattacherA(envoiId);
    }

    /** The topics are shared by all bank services (Guide V1 §8). */
    private boolean nousConcerne(AccuseEngine accuse) {
        String notre = proprietes.codeService();
        if (notre == null || notre.isBlank() || accuse.serviceId() == null) {
            // Without a configured service code nothing can be asserted, so nothing is recorded.
            return false;
        }
        return notre.equals(accuse.serviceId());
    }
}
