package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodic pass over the external notification queue.
 * <p>
 * <b>Decoupled from the business action</b>, which only wrote a queue row in its own transaction: an
 * engine outage neither blocks nor rolls it back, and delivery is attempted and retried here.
 * <p>
 * Each row is handed to {@link RemiseNotification}, which processes it in its own transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatcherNotification {

    /** Notifications delivered per pass, so the queue drains smoothly. */
    static final int LOT = 50;

    private final NotificationEnvoiRepository notificationEnvoiRepository;
    private final RemiseNotification remiseNotification;
    /**
     * Opens the transaction explicitly: {@code @Transactional} on a method of this same class would be
     * ignored, Spring only applies it to calls coming through the proxy.
     */
    private final TransactionTemplate transactions;

    @Scheduled(fixedDelayString = "${notification.engine.dispatcher.interval-ms:60000}")
    public void remettreLesNotificationsEnAttente() {
        transactions.executeWithoutResult(statut -> abandonnerLesPiecesJointesSansReponse());
        for (Long id : aRemettre()) {
            try {
                remiseNotification.remettre(id);
            } catch (RuntimeException e) {
                // A failing row must not stop the pass.
                log.error("Remise impossible de la notification {}", id, e);
            }
        }
    }

    /** A single repository query: Spring Data runs it in its own read-only transaction. */
    List<Long> aRemettre() {
        return notificationEnvoiRepository.trouverARemettre(LocalDateTime.now(), PageRequest.of(0, LOT));
    }

    /**
     * A failed attachment upload must not hold the notification back: after the delay it is sent
     * without the document and the cause is recorded. Runs inside {@link #transactions}, so the change
     * of each row is flushed.
     */
    void abandonnerLesPiecesJointesSansReponse() {
        List<NotificationEnvoi> attentes = notificationEnvoiRepository
                .trouverEnAttenteDePieceJointe(LocalDateTime.now(), PageRequest.of(0, LOT));
        for (NotificationEnvoi envoi : attentes) {
            log.warn("Aucune adresse de dépôt reçue pour {} : la notification partira sans sa pièce jointe",
                    envoi.getEventId());
            envoi.pieceJointeAbandonnee("DEPOT_SANS_REPONSE");
        }
    }
}
