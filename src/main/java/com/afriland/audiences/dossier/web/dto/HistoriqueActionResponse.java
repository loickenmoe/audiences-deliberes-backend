package com.afriland.audiences.dossier.web.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record HistoriqueActionResponse(
        Long id,
        Long utilisateurId,
        String action,
        Map<String, Object> details,
        LocalDateTime dateAction) {
}
