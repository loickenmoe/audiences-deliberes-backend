package com.afriland.audiences.notification.passerelle;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Message as expected by the Notification Engine; field names follow the external contract.
 * <p>
 * The contract is taken from an actually accepted message, which differs from Guide V1:
 * <ul>
 *   <li>{@code serviceKey} accompanies the token;</li>
 *   <li>{@code priority} is carried <b>in the message</b>, not only on the service account;</li>
 *   <li>the recipient is given by {@code recipient}, even with {@code channelRecipients}.</li>
 * </ul>
 * {@code locale} and {@code metadata} are omitted: an unexpected field may make the engine reject the
 * message without any trace. A queue row targets one channel, so {@code recipient} is enough.
 * Null fields are not serialized (an SMS has no subject).
 *
 * @param token       short-lived HS256 token identifying the calling service; required by the engine
 * @param serviceKey  service account code, repeated outside the token
 * @param priority    message priority (the account's: STANDARD)
 * @param eventId     deduplication key: republishing does not duplicate the send
 * @param category    message family; our notifications are transactional
 * @param callbackUrl URL called back by the engine with the status; null if the application is not
 *                    reachable from the cluster
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageEngine(
        String token,
        String serviceKey,
        String priority,
        List<String> channels,
        String recipient,
        Map<String, String> channelRecipients,
        String directSubject,
        String directBody,
        String eventId,
        String category,
        String callbackUrl,
        List<PieceJointeEngine> attachments) {

    public static final String CATEGORIE = "TRANSACTIONAL";

    public static MessageEngine de(String token, String serviceKey, String priority, CanalEngine canal,
                                   String destinataire, String sujet, String corps, String eventId,
                                   String callbackUrl) {
        return de(token, serviceKey, priority, canal, destinataire, sujet, corps, eventId, callbackUrl, null);
    }

    /**
     * @param pieceJointe already uploaded to the engine; {@code null} if none, in which case the field
     *                    is not serialized
     */
    public static MessageEngine de(String token, String serviceKey, String priority, CanalEngine canal,
                                   String destinataire, String sujet, String corps, String eventId,
                                   String callbackUrl, PieceJointeEngine pieceJointe) {
        return new MessageEngine(token, serviceKey, priority, List.of(canal.name()), destinataire, null,
                sujet, corps, eventId, CATEGORIE, callbackUrl,
                pieceJointe == null ? null : List.of(pieceJointe));
    }

    /** The token and the callback URL carry secrets, so neither is logged. */
    public MessageEngine sansJeton() {
        return new MessageEngine(null, serviceKey, priority, channels, recipient, channelRecipients, directSubject,
                directBody, eventId, category, callbackUrl == null ? null : "(présente)", attachments);
    }
}
