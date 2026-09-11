package com.afriland.audiences.publication.web.dto;

import java.time.LocalDateTime;

/** {@code auteurNom} depuis Q-86. */
public record CorrespondanceResponse(Long id, Long publicationId, Long auteurId, String auteurNom, String contenu, LocalDateTime createdAt) {
}
