package com.afriland.audiences.notification.passerelle;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request for an attachment upload URL (Guide V1 §3 bis, topic {@code attachment-upload-request}).
 * The file content never goes through Kafka: the engine returns a single-use URL, the file is uploaded
 * there, and the notification only carries a storage key.
 *
 * @param requestId  our reference to match the response: the queue row's {@code eventId}, already unique
 * @param token      signed token, as for any message to the engine
 * @param serviceKey service account code
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DemandeDepotPieceJointe(
        String requestId,
        String fileName,
        String mimeType,
        String token,
        String serviceKey) {
}
