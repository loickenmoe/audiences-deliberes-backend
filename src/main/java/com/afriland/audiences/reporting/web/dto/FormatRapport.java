package com.afriland.audiences.reporting.web.dto;

/**
 * Format de sortie d'un rapport (FR-REP-01). Distinct de {@code audience.web.dto.FormatExport}
 * (Épic 2, 3 valeurs sans CSV) — le contrat en exige ici 4.
 */
public enum FormatRapport {
    JSON,
    PDF,
    EXCEL,
    CSV
}
