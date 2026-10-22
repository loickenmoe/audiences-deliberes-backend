package com.afriland.audiences.audience.service;

import com.afriland.audiences.audience.web.dto.CalendrierEntreeResponse;
import com.afriland.audiences.common.pdf.LecturePdf;
import org.junit.jupiter.api.DisplayName;
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

    /**
     * Checks what the file <b>contains</b>, not just that it is a PDF: a calendar that lost all its
     * rows would otherwise pass.
     */
    @Test
    @DisplayName("le PDF porte les en-tetes et les donnees de chaque entree")
    void exportPdf_contientLesDonnees() throws Exception {
        String texte = LecturePdf.texte(service.exportPdf(entrees()));

        // The seven headers, accents included: accented headers go through the TextePdf fallback, which
        // is exactly where a character can be lost unnoticed.
        assertThat(texte).contains("Référence").contains("Juridiction").contains("Étape")
                .contains("Client").contains("Agence d'origine").contains("Risque encouru").contains("Date")
                .contains("DOS-2026-001").contains("Client Test").contains("AG01")
                // The branding and the page numbering.
                .contains("Afriland First Bank").contains("Direction Juridique")
                .contains("Page 1 sur 1");
    }

    @Test
    @DisplayName("le fichier porte un titre, la ou il n'en avait aucun")
    void exportPdf_porteSesProprietes() throws Exception {
        // The file title carries the covered period, derived from the entries: two calendars extracted
        // two weeks apart must not be confused in a file explorer.
        assertThat(LecturePdf.proprietes(service.exportPdf(entrees())).getTitle())
                .startsWith("Calendrier des audiences - Du ");
    }

    /**
     * A court or client name may contain any character: without a fallback, the font throws and the
     * export fails.
     */
    @Test
    @DisplayName("une saisie hors encodage n'empeche pas l'export")
    void exportPdf_supporteUneSaisieHorsEncodage() {
        var exotique = List.of(new CalendrierEntreeResponse(1L, 2L, "DOS-2026-002", "Tribunal	Yaounde", "INSTANCE",
                List.of(10L), List.of(20L), "AG01", "Nguyen Thị", 5_000_000d, LocalDate.now()));

        assertThat(service.exportPdf(exotique)).isNotEmpty();
    }

    @Test
    void exportExcel_producesNonEmptyXlsxBytes() {
        byte[] xlsx = service.exportExcel(entrees());

        assertThat(xlsx).isNotEmpty();
        // ZIP signature (xlsx is an OOXML archive): "PK".
        assertThat(xlsx[0]).isEqualTo((byte) 'P');
        assertThat(xlsx[1]).isEqualTo((byte) 'K');
    }
}
