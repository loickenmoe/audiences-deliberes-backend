package com.afriland.audiences.reporting.service;

import com.afriland.audiences.common.pdf.LecturePdf;
import com.afriland.audiences.reporting.domain.TypeRapport;
import com.afriland.audiences.reporting.web.dto.RapportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M14: the three export formats of FR-REP-01. Locks the file header matching the announced format
 * and the actual presence of the indicators.
 */
class RapportExportServiceTest {

    private RapportExportService service;
    private RapportResponse rapport;

    @BeforeEach
    void setUp() {
        service = new RapportExportService();
        // The rate is still returned by the API, but the export no longer carries it: the three counts
        // must be there.
        rapport = new RapportResponse(TypeRapport.MENSUEL, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                4L, 3L, 2L, 1L, 0.75d, 3L, 1L, 0L, 150000d);
    }

    @Test
    void exportPdf_produitUnPdfNonVide() {
        byte[] pdf = service.exportPdf(rapport);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    /** Are the indicators really in the file, not only in the CSV? (A1) */
    @Test
    void exportPdf_contientLesIndicateurs() throws Exception {
        String texte = LecturePdf.texte(service.exportPdf(rapport));

        assertThat(texte).contains("MENSUEL").contains("2026-09-01").contains("2026-09-30");
        /*
         * Indicators are shown as a **table** (A2), not "label: value": check that each value stays on its
         * label's line, which is what matters and still reads in the extracted text.
         */
        assertThat(texte).containsPattern("Nouveaux dossiers\s+4");
        assertThat(texte).containsPattern("Audiences tenues\s+3");
        assertThat(texte).containsPattern("Litiges gagn.s\s+3");
        assertThat(texte).containsPattern("Litiges perdus\s+1");
        assertThat(texte).containsPattern("D.cisions mixtes\s+0");
        // Numbers, not a percentage: the rate has left the export.
        assertThat(texte).doesNotContain("Taux de succ");
        // The branding is on the page, and the page is numbered.
        assertThat(texte).contains("Afriland First Bank").contains("Page 1 sur 1");
    }

    @Test
    void exportPdf_porteSesProprietes() throws Exception {
        // The file title carries the scope, unlike the page title: two monthly reports for different
        // months must not be confused in a file explorer (A2).
        assertThat(LecturePdf.proprietes(service.exportPdf(rapport)).getTitle())
                .isEqualTo("Rapport MENSUEL - Du 2026-09-01 au 2026-09-30");
    }

    @Test
    void exportExcel_produitUnClasseurXlsx() {
        byte[] xlsx = service.exportExcel(rapport);

        assertThat(xlsx).isNotEmpty();
        // ZIP signature (OOXML): PK\x03\x04
        assertThat(xlsx[0]).isEqualTo((byte) 'P');
        assertThat(xlsx[1]).isEqualTo((byte) 'K');
    }

    @Test
    void exportCsv_contientLEnteteEtTousLesIndicateurs() {
        String csv = new String(service.exportCsv(rapport), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("Indicateur,Valeur");
        assertThat(csv).contains("Nouveaux dossiers,4");
        assertThat(csv).contains("Audiences tenues,3");
        assertThat(csv).contains("Demandes de frais déposées,2");
        assertThat(csv).contains("Délibérés enregistrés,1");
        assertThat(csv).contains("Provisions reprises,150000.0");
        // Numbers, not a percentage: the business reads the export as is.
        assertThat(csv).contains("Litiges gagnés,3");
        assertThat(csv).contains("Litiges perdus,1");
        assertThat(csv).contains("Décisions mixtes,0");
        assertThat(csv).doesNotContain("Taux de succès");
        assertThat(csv).doesNotContain("%");
    }

    @Test
    void exportCsv_surUnRapportVideResteStructurellementValide() {
        RapportResponse vide = new RapportResponse(TypeRapport.JOURNALIER, LocalDate.now(), LocalDate.now(),
                0L, 0L, 0L, 0L, 0d, 0L, 0L, 0L, 0d);

        String csv = new String(service.exportCsv(vide), StandardCharsets.UTF_8);

        assertThat(csv.lines()).hasSize(9); // header + 8 indicators
        assertThat(vide.estVide()).isTrue();
    }
}
