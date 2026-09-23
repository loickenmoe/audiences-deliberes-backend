package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Passage régulier sur la file des notifications externes (NOTIFICATION_ENGINE_PLAN.md, étape 8).
 * <p>
 * <b>Séparé de l'action métier</b> (D12) : celle-ci a seulement écrit une ligne dans la file, dans sa
 * propre transaction. Une panne du moteur ne l'a donc ni bloquée ni annulée, et c'est ici, plus tard,
 * que la remise est tentée puis retentée (D13).
 * <p>
 * Le passage lit les identifiants à traiter, puis confie chaque ligne à {@link RemiseNotification},
 * qui la traite dans sa propre transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatcherNotification {

    /** Nombre de notifications remises par passage : la file se vide progressivement, sans à-coup. */
    static final int LOT = 50;

    private final NotificationEnvoiRepository notificationEnvoiRepository;
    private final RemiseNotification remiseNotification;

    @Scheduled(fixedDelayString = "${notification.engine.dispatcher.interval-ms:60000}")
    public void remettreLesNotificationsEnAttente() {
        for (Long id : aRemettre()) {
            try {
                remiseNotification.remettre(id);
            } catch (RuntimeException e) {
                // Une ligne en échec ne doit pas arrêter le passage : les suivantes partent quand même.
                log.error("Remise impossible de la notification {}", id, e);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Long> aRemettre() {
        return notificationEnvoiRepository.trouverARemettre(LocalDateTime.now(), PageRequest.of(0, LOT));
    }
}
