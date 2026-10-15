package com.afriland.audiences.ged.web.dto;

import com.afriland.audiences.ged.domain.FormatDocument;

import java.time.LocalDateTime;

/** {@code urlTelechargement} : URL pré-signée MinIO (FR-GED-02), {@code null} sur la liste (#43) — générée à la demande sur {@code GET /ged/documents/{id}} uniquement. */
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
        /** Partagé avec l'avocat, proposé, ou réservé aux profils internes (QF-64). */
        com.afriland.audiences.ged.domain.StatutPartageAvocat partageAvocat) {
}
