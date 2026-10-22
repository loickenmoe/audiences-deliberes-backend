package com.afriland.audiences.reporting.web.dto;

/**
 * Report output format (FR-REP-01). Distinct from {@code audience.web.dto.FormatExport}, which has
 * no CSV value; the reporting contract requires all four.
 */
public enum FormatRapport {
    JSON,
    PDF,
    EXCEL,
    CSV
}
