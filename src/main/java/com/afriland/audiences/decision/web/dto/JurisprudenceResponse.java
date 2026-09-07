package com.afriland.audiences.decision.web.dto;

import com.afriland.audiences.decision.domain.TypeArchiveJurisprudence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record JurisprudenceResponse(
        Long id,
        Long dossierId,
        TypeArchiveJurisprudence typeArchive,
        List<String> motsCles,
        String natureDecision,
        String juridiction,
        LocalDate dateDecision,
        String urlTelechargement,
        LocalDateTime createdAt) {
}
