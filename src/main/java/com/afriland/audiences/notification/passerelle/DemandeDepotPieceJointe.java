package com.afriland.audiences.notification.passerelle;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Demande d'une URL de dépôt pour une pièce jointe (Guide V1 §3 bis, topic
 * {@code attachment-upload-request}).
 * <p>
 * Le contenu du fichier ne transite jamais par Kafka : le moteur renvoie une URL à usage unique, le
 * fichier y est déposé directement, et la notification ne porte ensuite qu'une clé de stockage.
 *
 * @param requestId  référence que nous choisissons pour retrouver la réponse : l'{@code eventId} de
 *                   la ligne de la file, qui est déjà unique
 * @param token      jeton signé, comme pour toute publication vers le moteur
 * @param serviceKey code du compte de service, que le moteur attend partout ailleurs
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DemandeDepotPieceJointe(
        String requestId,
        String fileName,
        String mimeType,
        String token,
        String serviceKey) {
}
