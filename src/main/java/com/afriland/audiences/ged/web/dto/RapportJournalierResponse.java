package com.afriland.audiences.ged.web.dto;

import java.time.LocalDate;
import java.util.List;

/** FR-GED-04 : zéros/liste vide si aucune donnée pour la date, jamais d'erreur. */
public record RapportJournalierResponse(
        LocalDate date,
        long dossiersManipules,
        long piecesAjoutees,
        long affairesNouvelles,
        List<ActePlanifieResponse> planificationActes) {
}
