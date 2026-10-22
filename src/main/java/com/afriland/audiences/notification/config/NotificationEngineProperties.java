package com.afriland.audiences.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Notification Engine settings, read from the {@code .env} file (never committed).
 *
 * @param actif          master switch; {@code false} by default: no notification leaves the
 *                       application, they are only logged
 * @param codeService    service account code assigned by the administrator (JWT {@code sub})
 * @param jwtSecret      HS256 token signing secret; at least 32 characters (Guide V1 §4 bis)
 * @param tentativesMax  delivery attempts before permanent failure
 * @param ageMax         beyond this age a pending notification expires instead of being sent
 * @param redirection     sandbox for non-production environments
 * @param callbackBaseUrl public application URL reachable by the engine; empty on a workstation, which
 *                        disables callbacks
 * @param priorite        priority carried by each message; the service account's (STANDARD)
 * @param delaiDepot      beyond this delay, a notification waiting for its attachment upload URL is
 *                        sent without the attachment
 * @param suiviAccuses    whether the engine actually publishes receipts. <b>False by default</b>: the
 *                        tracking topics are empty for all services, so a delivered notification
 *                        cannot be confirmed and waiting would wrongly mark it SANS_RETOUR
 */
@ConfigurationProperties(prefix = "notification.engine")
public record NotificationEngineProperties(
        @DefaultValue("false") boolean actif,
        String codeService,
        String jwtSecret,
        @DefaultValue("5") int tentativesMax,
        @DefaultValue("2h") Duration ageMax,
        @DefaultValue Redirection redirection,
        @DefaultValue Transport transport,
        String callbackBaseUrl,
        @DefaultValue("STANDARD") String priorite,
        @DefaultValue("false") boolean suiviAccuses,
        @DefaultValue("5m") Duration delaiDepot) {

    /** Minimum secret length required by the engine for HS256 signing (256 bits). */
    public static final int LONGUEUR_MIN_SECRET = 32;

    /**
     * Delays before each retry: 1 min, 5 min, 15 min, 1 h, then 6 h. Past the maximum number of
     * attempts, the notification fails and shows in monitoring.
     */
    public static final List<Duration> DELAIS_TENTATIVES = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15),
            Duration.ofHours(1), Duration.ofHours(6));

    /**
     * Message transport to the engine.
     *
     * @param type            DAPR (documented by the Guide), KAFKA (direct fallback) or JOURNAL (nothing
     *                        is sent; useful to test an active configuration without sending)
     * @param topic           engine input topic. Accepted messages arrive on
     *                        {@code notification-send-request}, with the priority inside the message;
     *                        the Guide's {@code notification-send-standard} is not consumed.
     * @param pubsub          pubsub component name, as seen in accepted messages
     * @param daprUrl         local sidecar URL
     * @param attenteSecondes broker acknowledgement timeout, for direct Kafka transport
     * @param cloudEvents     for direct Kafka transport, reproduce the Dapr sidecar envelope. True by
     *                        default, as the engine expects it
     */
    public record Transport(
            @DefaultValue("DAPR") TypeTransport type,
            @DefaultValue("notification-send-request") String topic,
            @DefaultValue("kafka-pubsub") String pubsub,
            @DefaultValue("http://localhost:3500") String daprUrl,
            @DefaultValue("10") long attenteSecondes,
            @DefaultValue("true") boolean cloudEvents,
            @DefaultValue("attachment-upload-request") String topicDepot) {
    }

    public enum TypeTransport {
        DAPR, KAFKA, JOURNAL
    }

    /** Masks the secret: this record may end up in a log or an error message. */
    @Override
    public String toString() {
        return "NotificationEngineProperties[actif=" + actif + ", codeService=" + codeService
                + ", jwtSecret=" + (jwtSecret == null || jwtSecret.isBlank() ? "(absent)" : "***")
                + ", tentativesMax=" + tentativesMax + ", ageMax=" + ageMax + ", redirection=" + redirection + "]";
    }

    /**
     * Non-production redirection. DEV data contains real lawyer numbers: while active, no message
     * reaches a real recipient, and the original recipient is kept in the queue for checking.
     *
     * @param courrielInterne   replaces the work address (Outlook, Teams)
     * @param courrielPersonnel replaces a provider's personal e-mail
     * @param telephone         replaces the number (SMS, WhatsApp), in the Guide's format
     */
    public record Redirection(
            @DefaultValue("true") boolean actif,
            String courrielInterne,
            String courrielPersonnel,
            String telephone) {

        public boolean complete() {
            return estRenseigne(courrielInterne) && estRenseigne(courrielPersonnel) && estRenseigne(telephone);
        }

        private static boolean estRenseigne(String valeur) {
            return valeur != null && !valeur.isBlank();
        }
    }
}
