package com.afriland.audiences.notification.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Réponse du moteur à une demande de dépôt (Guide V1 §3 bis, topic
 * {@code attachment-upload-response}). Les noms des champs sont ceux du moteur.
 *
 * @param requestId       la référence que nous avions choisie : l'{@code eventId} de la ligne
 * @param status          {@code READY} ou {@code FAILED}
 * @param storageKey      clé à citer dans la notification, si {@code READY}
 * @param presignedPutUrl adresse à usage unique où déposer le fichier, valable quinze minutes
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
