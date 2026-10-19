package com.afriland.audiences.portail.web.dto;

/** Pre-signed URL of a shared document, valid for ten minutes like any document-store URL. */
public record PieceTelechargementResponse(Long id, String nomFichier, String urlTelechargement) {
}
