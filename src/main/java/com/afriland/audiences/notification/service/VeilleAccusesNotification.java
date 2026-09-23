package com.afriland.audiences.notification.service;

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
 * Surveille les notifications remises au moteur dont aucun accusé n'arrive (Q-120).
 * <p>
 * <b>Pourquoi cet état existe.</b> Un message dont le jeton est invalide est rejeté « sans aucun
 * retour » (Guide V1 §4 bis) : aucun accusé de succès, aucun accusé d'échec. Sans cette veille, ces
 * messages resteraient « publiés » pour toujours, et l'on croirait la notification partie. Passés
 * en SANS_RETOUR, ils deviennent visibles en supervision (écran 41) et se distinguent nettement
 * d'un envoi confirmé.
 * <p>
 * Ce délai n'est pas une règle métier : c'est le temps que l'on accorde au moteur avant de
 * considérer son silence comme une réponse.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VeilleAccusesNotification {

    private static final int LOT = 200;

    private final NotificationEnvoiRepository notificationEnvoiRepository;

    @Value("${notification.engine.delai-accuse:15m}")
    private Duration delaiAccuse;

    @Scheduled(fixedDelayString = "${notification.engine.veille-accuses.interval-ms:300000}")
    @Transactional
    public void marquerLesEnvoisSansRetour() {
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
