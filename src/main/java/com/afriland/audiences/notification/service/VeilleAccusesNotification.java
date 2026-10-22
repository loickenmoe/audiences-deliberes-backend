package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.config.NotificationEngineProperties;
import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Watches notifications delivered to the engine that never receive a receipt.
 * <p>
 * A message with an invalid token is rejected without any feedback (Guide V1 §4 bis). Without this
 * watcher such messages would stay "published" forever; marked SANS_RETOUR, they become visible in
 * monitoring. The delay is not a business rule, just how long the engine is given before its silence
 * counts as an answer.
 * <p>
 * In DEV the tracking topics are empty for all services, so the watcher does nothing while
 * {@code notification.engine.suivi-accuses} is false.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VeilleAccusesNotification {

    private static final int LOT = 200;

    private final NotificationEnvoiRepository notificationEnvoiRepository;
    private final NotificationEngineProperties proprietes;

    @Value("${notification.engine.delai-accuse:15m}")
    private Duration delaiAccuse;

    @Scheduled(fixedDelayString = "${notification.engine.veille-accuses.interval-ms:300000}")
    @Transactional
    public void marquerLesEnvoisSansRetour() {
        if (!proprietes.suiviAccuses()) {
            // The engine publishes no receipts: their absence proves nothing, and everything would
            // otherwise switch to SANS_RETOUR, including delivered messages.
            return;
        }
        LocalDateTime limite = LocalDateTime.now().minus(delaiAccuse);
        List<NotificationEnvoi> sansRetour = notificationEnvoiRepository
                .trouverPublieesSansAccuse(limite, PageRequest.of(0, LOT));
        if (sansRetour.isEmpty()) {
            return;
        }
        sansRetour.forEach(NotificationEnvoi::marquerSansRetour);
        log.warn("{} notification(s) publiée(s) depuis plus de {} sans accusé du moteur : passées en SANS_RETOUR",
                sansRetour.size(), delaiAccuse);
    }
}
