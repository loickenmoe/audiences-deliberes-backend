package com.afriland.audiences.notification.passerelle;

/**
 * Single entry point for delivering a message to the Notification Engine. The transport (Dapr sidecar
 * or direct Kafka) is selected by configuration.
 * <p>
 * While {@code notification.engine.actif} is {@code false} (default), {@link PasserelleJournal} is
 * used and nothing is sent.
 */
public interface PasserelleNotificationEngine {

    /**
     * Hands the message to the transport. An exception means delivery failed and may be retried;
     * actual delivery is tracked separately ({@code notification-sent} and {@code notification-failed}).
     *
     * @return name of the transport used, recorded in the queue for diagnostics
     */
    String publier(MessageEngine message);

    /**
     * Requests an attachment upload URL from the engine (Guide V1 §3 bis). The response arrives later
     * on a topic, carrying the request's {@code requestId}.
     *
     * @return {@code false} if this transport cannot request uploads; the notification is then sent
     *         without its attachment
     */
    default boolean demanderDepot(DemandeDepotPieceJointe demande) {
        return false;
    }
}
