package com.afriland.audiences.referentiel.web.dto;

import java.time.LocalDateTime;

public record ClientResponse(
        Long id,
        String reference,
        String nom,
        String email,
        String telephone,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
