package com.afriland.audiences.delibere.web.dto;

import java.time.LocalDateTime;

/**
 * Read-only: {@code GET /deliberes/{id}/recours} never creates an alert; tracking relies on
 * {@code Delibere.dateEcheanceRecours} and {@code DelaiRecoursScheduler}.
 */
public record RecoursResponse(
        Long delibereId,
        LocalDateTime dateEcheanceRecours,
        boolean recoursExerce,
        boolean delaiExpire,
        boolean cloture) {
}
