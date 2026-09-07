package com.afriland.audiences.publication.web.dto;

import java.time.LocalDateTime;

public record CorrespondanceResponse(Long id, Long publicationId, Long auteurId, String contenu, LocalDateTime createdAt) {
}
