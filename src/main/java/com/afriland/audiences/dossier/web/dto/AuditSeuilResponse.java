package com.afriland.audiences.dossier.web.dto;

import com.afriland.audiences.dossier.domain.StatutValidation;

import java.time.LocalDateTime;

public record AuditSeuilResponse(
        Long id,
        Long dossierId,
        Long utilisateurId,
        Double ancienSeuil,
        Double nouveauSeuil,
        String motif,
        Long validePar,
        StatutValidation statut,
        LocalDateTime dateAction) {
}
