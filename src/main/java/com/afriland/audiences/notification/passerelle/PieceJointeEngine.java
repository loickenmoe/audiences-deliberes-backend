package com.afriland.audiences.notification.passerelle;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Attachment referenced by a notification (Guide V1 §3 bis). The file itself was uploaded
 * beforehand: only its storage key is sent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PieceJointeEngine(String fileName, String mimeType, String storageKey) {
}
