package com.afriland.audiences.publication.web.dto;

import java.time.LocalDateTime;

/** {@code auteurNom}: the DJ and DJA know which legal officer wrote what. */
public record CommentaireResponse(Long id, Long publicationId, Long auteurId, String auteurNom, String contenu, LocalDateTime createdAt) {
}
