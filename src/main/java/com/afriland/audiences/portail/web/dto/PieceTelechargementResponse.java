package com.afriland.audiences.portail.web.dto;

/** L'URL pré-signée d'une pièce partagée, valable dix minutes comme toute URL de la GED (QF-64). */
public record PieceTelechargementResponse(Long id, String nomFichier, String urlTelechargement) {
}
