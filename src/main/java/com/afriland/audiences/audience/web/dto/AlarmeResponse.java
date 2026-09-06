package com.afriland.audiences.audience.web.dto;

import com.afriland.audiences.audience.domain.StatutAlarme;

import java.time.LocalDateTime;

public record AlarmeResponse(
        Long id,
        Long dossierId,
        Long etapeId,
        String objet,
        LocalDateTime dateEcheance,
        StatutAlarme statut,
        LocalDateTime dateTraitement,
        Long alarmePrecedenteId,
        Long createdBy,
        LocalDateTime createdAt) {
}
