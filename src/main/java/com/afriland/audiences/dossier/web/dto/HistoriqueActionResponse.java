package com.afriland.audiences.dossier.web.dto;

import com.afriland.audiences.common.audit.TypeActionHistorique;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * A history entry.
 *
 * @param code    typed nature of the action; authoritative and used by filters. {@code action} is
 * only its readable wording.
 * @param details structured details: step name, decision outcome, configuration key
 */
public record HistoriqueActionResponse(
        Long id,
        Long utilisateurId,
        TypeActionHistorique code,
        String action,
        Map<String, Object> details,
        LocalDateTime dateAction) {
}
