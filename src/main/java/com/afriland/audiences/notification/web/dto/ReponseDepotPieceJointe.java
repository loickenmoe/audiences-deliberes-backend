package com.afriland.audiences.notification.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Engine response to an upload request (Guide V1 §3 bis, topic {@code attachment-upload-response}).
 * Field names are the engine's.
 *
 * @param requestId       the reference we chose: the row's {@code eventId}
 * @param status          {@code READY} or {@code FAILED}
 * @param storageKey      key to cite in the notification, when {@code READY}
 * @param presignedPutUrl single-use upload URL, valid for fifteen minutes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReponseDepotPieceJointe(
        String requestId,
        String status,
        String storageKey,
        String presignedPutUrl,
        String expiresAt,
        String errorMessage) {

    public static final String PRET = "READY";

    public boolean prete() {
        return PRET.equals(status) && storageKey != null && presignedPutUrl != null;
    }
}
