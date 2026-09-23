package com.afriland.audiences.notification.passerelle;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Pièce jointe citée dans une notification (Guide V1 §3 bis). Le fichier lui-même a été déposé
 * auparavant : seule sa clé de stockage voyage.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PieceJointeEngine(String fileName, String mimeType, String storageKey) {
}
