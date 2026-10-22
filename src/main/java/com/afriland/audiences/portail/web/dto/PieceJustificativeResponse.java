package com.afriland.audiences.portail.web.dto;

/**
 * A stored supporting document. {@code cle} is the storage path, to be copied as is into
 * {@code piecesJustificatives} when filing the request ({@code pieces_justificatives TEXT[]} holds
 * document paths). {@code nomFichier} is the original, human-readable file name.
 */
public record PieceJustificativeResponse(
        String cle,
        String nomFichier,
        String urlTelechargement) {
}
