package com.afriland.audiences.ged.web.dto;

import com.afriland.audiences.ged.domain.FormatDocument;

import java.time.LocalDateTime;

/** {@code urlTelechargement}: presigned MinIO URL (FR-GED-02), {@code null} in lists; generated only on {@code GET /ged/documents/{id}}. */
public record DocumentResponse(
        Long id,
        Long dossierId,
        String nomFichier,
        String typeDocument,
        FormatDocument format,
        Long poids,
        Long auteurChargementId,
        LocalDateTime dateChargement,
        boolean estSupprime,
        String urlTelechargement,
        /** Shared with the lawyer, proposed, or restricted to internal profiles. */
        com.afriland.audiences.ged.domain.StatutPartageAvocat partageAvocat) {
}
