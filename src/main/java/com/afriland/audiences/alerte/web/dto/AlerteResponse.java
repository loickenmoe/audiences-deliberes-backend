package com.afriland.audiences.alerte.web.dto;

import com.afriland.audiences.alerte.domain.CanalAlerte;
import com.afriland.audiences.alerte.domain.StatutAlerte;
import com.afriland.audiences.alerte.domain.TypeAlerte;

import java.time.LocalDateTime;

public record AlerteResponse(
        Long id,
        Long dossierId,
        Long destinataireId,
        TypeAlerte type,
        String declencheur,
        CanalAlerte canal,
        StatutAlerte statut,
        LocalDateTime dateDeclenchement,
        LocalDateTime dateTraitement) {
}
