package com.afriland.audiences.delibere.web.dto;

import java.time.LocalDateTime;

/**
 * Consultation seule (Q-19) — {@code GET /deliberes/{id}/recours} ne crée jamais d'alerte ;
 * le suivi est porté par {@code Delibere.dateEcheanceRecours} et {@code DelaiRecoursScheduler}.
 */
public record RecoursResponse(
        Long delibereId,
        LocalDateTime dateEcheanceRecours,
        boolean recoursExerce,
        boolean delaiExpire,
        boolean cloture) {
}
