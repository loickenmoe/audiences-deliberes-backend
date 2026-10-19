package com.afriland.audiences.common.audit;

/**
 * Audit export formats: PDF for a fixed document handed to an inspector, Excel for data to sort and
 * cross-check.
 */
public enum FormatExportAudit {
    PDF("application/pdf", "pdf"),
    EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

    private final String typeMime;
    private final String extension;

    FormatExportAudit(String typeMime, String extension) {
        this.typeMime = typeMime;
        this.extension = extension;
    }

    public String typeMime() {
        return typeMime;
    }

    public String extension() {
        return extension;
    }
}
