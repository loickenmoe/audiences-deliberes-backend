package com.afriland.audiences.ged.web.dto;

import java.time.LocalDate;
import java.util.List;

/** FR-GED-04: zeros/empty list if there is no data for the date, never an error. */
public record RapportJournalierResponse(
        LocalDate date,
        long dossiersManipules,
        long piecesAjoutees,
        long affairesNouvelles,
        List<ActePlanifieResponse> planificationActes) {
}
