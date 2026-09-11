package com.afriland.audiences.intervenant.web.dto;

import com.afriland.audiences.intervenant.domain.TypeIntervenant;

import java.time.LocalDateTime;

public record IntervenantResponse(
        Long id,
        TypeIntervenant type,
        String nom,
        String email,
        String telephone,
        String compteKeycloak,
        Boolean notification,
        LocalDateTime createdAt) {
}
