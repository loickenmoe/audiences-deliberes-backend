package com.afriland.audiences.delibere.web.dto;

import com.afriland.audiences.delibere.domain.ResultatDelibere;
import com.afriland.audiences.delibere.domain.StatutExpedition;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DelibereResponse(
        Long id,
        Long dossierId,
        Long etapeId,
        LocalDate dateDeliberee,
        ResultatDelibere resultat,
        StatutExpedition statutExpedition,
        LocalDateTime dateEcheanceRecours,
        LocalDateTime createdAt) {
}
