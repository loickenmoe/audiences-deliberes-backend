package com.afriland.audiences.reporting.service;

import com.afriland.audiences.reporting.domain.TypeRapport;
import com.afriland.audiences.reporting.web.dto.RapportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M14 — les trois formats d'export de FR-REP-01 n'étaient couverts que par la validation manuelle
 * (signatures de fichiers vérifiées via curl en M13). Ces tests les figent : en-tête de fichier
 * conforme au format annoncé et présence effective des indicateurs.
 */
class RapportExportServiceTest {

    private RapportExportService service;
    private RapportResponse rapport;

    @BeforeEach
    void setUp() {
        service = new RapportExportService();
        rapport = new RapportResponse(TypeRapport.MENSUEL, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                4L, 3L, 2L, 1L, 0.75d, 150000d);
    }

    @Test
    void exportPdf_produitUnPdfNonVide() {
        byte[] pdf = service.exportPdf(rapport);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void exportExcel_produitUnClasseurXlsx() {
        byte[] xlsx = service.exportExcel(rapport);

        assertThat(xlsx).isNotEmpty();
        // Signature ZIP (OOXML) : PK\x03\x04
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
        assertThat(csv).containsPattern("Taux de succès recouvrement,75[.,]00 %");
    }

    @Test
    void exportCsv_surUnRapportVideResteStructurellementValide() {
        RapportResponse vide = new RapportResponse(TypeRapport.JOURNALIER, LocalDate.now(), LocalDate.now(),
                0L, 0L, 0L, 0L, 0d, 0d);

        String csv = new String(service.exportCsv(vide), StandardCharsets.UTF_8);

        assertThat(csv.lines()).hasSize(7); // en-tête + 6 indicateurs
        assertThat(vide.estVide()).isTrue();
    }
}
