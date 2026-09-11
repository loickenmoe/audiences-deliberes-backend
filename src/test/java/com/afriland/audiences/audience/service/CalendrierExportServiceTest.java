package com.afriland.audiences.audience.service;

import com.afriland.audiences.audience.web.dto.CalendrierEntreeResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalendrierExportServiceTest {

    private final CalendrierExportService service = new CalendrierExportService();

    private List<CalendrierEntreeResponse> entrees() {
        return List.of(new CalendrierEntreeResponse(1L, 2L, "DOS-2026-001", "Tribunal de Yaoundé", "INSTANCE",
                List.of(10L), List.of(20L), "AG01", "Client Test", 5_000_000d, LocalDate.now().plusDays(5)));
    }

    @Test
    void exportPdf_producesNonEmptyPdfBytes() {
        byte[] pdf = service.exportPdf(entrees());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void exportPdf_handlesEmptyList() {
        byte[] pdf = service.exportPdf(List.of());

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void exportExcel_producesNonEmptyXlsxBytes() {
        byte[] xlsx = service.exportExcel(entrees());

        assertThat(xlsx).isNotEmpty();
        // Signature ZIP (xlsx = archive OOXML) : "PK".
        assertThat(xlsx[0]).isEqualTo((byte) 'P');
        assertThat(xlsx[1]).isEqualTo((byte) 'K');
    }
}
