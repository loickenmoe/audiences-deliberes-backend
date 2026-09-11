package com.afriland.audiences.publication.web.dto;

import java.time.LocalDateTime;

/** {@code auteurNom} depuis Q-86 : le DJ et la DJA savent quel juriste a écrit quoi. */
public record CommentaireResponse(Long id, Long publicationId, Long auteurId, String auteurNom, String contenu, LocalDateTime createdAt) {
}
