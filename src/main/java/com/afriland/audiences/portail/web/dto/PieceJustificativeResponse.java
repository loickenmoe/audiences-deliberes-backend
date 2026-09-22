package com.afriland.audiences.portail.web.dto;

/**
 * Une pièce justificative stockée (Q-97).
 *
 * <p>{@code cle} est le **chemin de stockage**, à reporter tel quel dans
 * {@code piecesJustificatives} au moment de déposer la demande — c'est exactement ce que le
 * dictionnaire des données prévoit pour {@code pieces_justificatives TEXT[]} : « chemins ou
 * références des pièces ». {@code nomFichier} est le nom d'origine, seul lisible par un humain.
 */
public record PieceJustificativeResponse(
        String cle,
        String nomFichier,
        String urlTelechargement) {
}
