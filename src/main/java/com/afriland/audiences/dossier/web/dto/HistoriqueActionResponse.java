package com.afriland.audiences.dossier.web.dto;

import com.afriland.audiences.common.audit.TypeActionHistorique;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Une ligne d'historique.
 *
 * @param code    nature typée de l'action (Q-111). C'est elle qui fait foi et sur laquelle portent
 *                les filtres ; {@code action} n'en est que la formulation lisible, non traduisible
 *                en l'état (QF-24).
 * @param details détails structurés — y compris ce qui figurait autrefois dans la phrase : nom de
 *                l'étape, sens d'une décision, clé de configuration
 */
public record HistoriqueActionResponse(
        Long id,
        Long utilisateurId,
        TypeActionHistorique code,
        String action,
        Map<String, Object> details,
        LocalDateTime dateAction) {
}
