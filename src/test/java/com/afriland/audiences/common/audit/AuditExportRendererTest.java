package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.audit.AuditExportRenderer.LigneExport;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provenance carried by the file itself.
 *
 * <p>A file gets renamed, forwarded, attached without context; whatever is not <em>in</em> the
 * document is lost. These tests check that the reference and the period survive in the file alone,
 * in <b>both formats</b> (PDF and Excel). Layout is not checked.
 */
class AuditExportRendererTest {

    private static final LocalDate DEBUT = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 9, 17);
    private static final String TITRE = "Audit AUD-2026-0042 - du 2026-09-01 au 2026-09-17";

    private final AuditExportRenderer renderer = new AuditExportRenderer();

    private static List<LigneExport> deuxLignes() {
        return List.of(
                new LigneExport("01/09/2026 08:30", "Paule Directrice", "D-2026-001", "Création du dossier", ""),
                new LigneExport("02/09/2026 09:15", "Paul Adjoint", "-", "Modification d'un paramètre", "Portée : TOUT"));
    }

    @Test
    @DisplayName("le PDF porte sa référence, sa période et son auteur")
    void pdfPorteSaProvenance() throws Exception {
        byte[] contenu = renderer.pdf(deuxLignes(), DEBUT, FIN, TITRE, "Paule Directrice");

        try (PDDocument document = Loader.loadPDF(contenu)) {
            String texte = new PDFTextStripper().getText(document);

            assertThat(texte).contains("AUD-2026-0042");
            assertThat(texte).contains("2026-09-01").contains("2026-09-17");
            assertThat(texte).contains("Paule Directrice");
            // And the explanation of the fingerprint: without it, the recipient does not know it can be
            // verified, and the fingerprint is useless.
            assertThat(texte).contains("SHA-256");
            // The common branding.
            assertThat(texte).contains("Afriland First Bank").contains("Direction Juridique");
            assertThat(texte).contains("Page 1 sur");
        }
    }

    @Test
    @DisplayName("le classeur porte la même provenance, sur une feuille qui ne gêne pas la lecture")
    void excelPorteSaProvenance() throws Exception {
        byte[] contenu = renderer.excel(deuxLignes(), DEBUT, FIN, TITRE, "Paule Directrice");

        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(contenu))) {
            // Rows first: they are what the reader opens the workbook for.
            assertThat(classeur.getSheetAt(0).getSheetName()).isEqualTo("Audit");
            assertThat(classeur.getSheetAt(0).getLastRowNum()).isEqualTo(2); // header + 2 rows

            XSSFSheet provenance = classeur.getSheet("Provenance");
            assertThat(provenance).as("feuille de provenance").isNotNull();

            StringBuilder texte = new StringBuilder();
            provenance.forEach(ligne -> texte.append(ligne.getCell(0).getStringCellValue()).append('\n'));

            assertThat(texte.toString())
                    .contains("AUD-2026-0042")
                    .contains("2026-09-01")
                    .contains("2026-09-17")
                    .contains("Paule Directrice")
                    .contains("2 ")
                    .contains("SHA-256");
        }
    }

    /**
     * A missing author must not print "null" in the "extracted by" field. It happens for extractions
     * triggered by a batch job; a dash is shown instead.
     */
    @Test
    @DisplayName("un auteur inconnu s'affiche en tiret, jamais en « null »")
    void auteurAbsentResteLisible() throws Exception {
        byte[] contenu = renderer.excel(deuxLignes(), DEBUT, FIN, TITRE, null);

        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(contenu))) {
            StringBuilder texte = new StringBuilder();
            classeur.getSheet("Provenance")
                    .forEach(ligne -> texte.append(ligne.getCell(0).getStringCellValue()).append('\n'));

            assertThat(texte.toString()).contains("Extrait par : -").doesNotContain("null");
        }
    }

    /**
     * The colophon must remain the <b>last</b> page: placed elsewhere it would read as a preamble, and
     * a reader skimming would stop before learning how to verify the document.
     */
    @Test
    @DisplayName("le colophon ferme le document, après les lignes")
    void colophonEnDernierePage() throws Exception {
        byte[] contenu = renderer.pdf(beaucoupDeLignes(200), DEBUT, FIN, TITRE, "Paule Directrice");

        try (PDDocument document = Loader.loadPDF(contenu)) {
            int pages = document.getNumberOfPages();
            assertThat(pages).as("200 lignes plus le colophon").isGreaterThan(2);

            PDFTextStripper lecteur = new PDFTextStripper();
            lecteur.setStartPage(pages);
            lecteur.setEndPage(pages);
            String derniere = lecteur.getText(document);

            assertThat(derniere).contains("Periode couverte").contains("SHA-256");
            /*
             * And the last page carries no audit row: the colophon is alone. A full reference is targeted
             * because "D-2026-" alone is contained in "AUD-2026-0042", which appears in every page title.
             */
            assertThat(derniere).doesNotContain("D-2026-100").doesNotContain("Modification d'un parametre");
        }
    }

    /**
     * Across 1,500 rows, losing the headers on page 2 would make the columns unreadable, and that is
     * exactly what an auditor reads.
     */
    @Test
    @DisplayName("les en-têtes de colonnes sont répétés sur chaque page de lignes")
    void entetesRepetes() throws Exception {
        byte[] contenu = renderer.pdf(beaucoupDeLignes(200), DEBUT, FIN, TITRE, "Paule Directrice");

        try (PDDocument document = Loader.loadPDF(contenu)) {
            String texte = new PDFTextStripper().getText(document);
            long avecEntete = texte.lines().filter(l -> l.contains("Horodatage") || l.contains("Auteur")).count();
            // Every page except the colophon.
            assertThat(avecEntete).isEqualTo(document.getNumberOfPages() - 1L);
        }
    }

    /**
     * A long "Details" value must appear in full, wrapped over several lines, including a word wider
     * than the column such as a fingerprint.
     */
    @Test
    @DisplayName("un détail plus long que sa colonne est écrit en entier, sans « ... »")
    void detailLongEcritEnEntier() throws Exception {
        String empreinte = "59bde5c17a2f4e0b9c8d7e6f5a4b3c2d1e0f9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7e6f5a4b";
        String details = "perimetre = TOUT; dateDebut = 2026-09-01; dateFin = 2026-09-17; format = PDF; "
                + "lignes = 1532; empreinte = " + empreinte + "; reference = AUD-2026-0042; FIN-DU-DETAIL";
        byte[] contenu = renderer.pdf(List.of(
                new LigneExport("01/09/2026 08:30", "Paule Directrice", "-", "Extraction de l'audit", details)),
                DEBUT, FIN, TITRE, "Paule Directrice");

        String texte;
        try (PDDocument document = Loader.loadPDF(contenu)) {
            texte = new PDFTextStripper().getText(document);
        }
        assertThat(texte).contains("FIN-DU-DETAIL").contains("lignes = 1532").doesNotContain("...");
        // The fingerprint is wider than the column: broken by character, it must be found whole once
        // line breaks are removed.
        assertThat(texte.replaceAll("\\s", "")).contains(empreinte);
    }

    /** No row may be lost across a page break. */
    @Test
    @DisplayName("aucune ligne n'est perdue entre les pages")
    void aucuneLignePerdue() throws Exception {
        byte[] contenu = renderer.pdf(beaucoupDeLignes(200), DEBUT, FIN, TITRE, null);

        String texte;
        try (PDDocument document = Loader.loadPDF(contenu)) {
            texte = new PDFTextStripper().getText(document);
        }
        assertThat(texte).contains("D-2026-000").contains("D-2026-100").contains("D-2026-199");
    }

    private static List<LigneExport> beaucoupDeLignes(int nombre) {
        return java.util.stream.IntStream.range(0, nombre)
                .mapToObj(i -> new LigneExport("01/09/2026 08:%02d".formatted(i % 60), "Paul Adjoint",
                        "D-2026-%03d".formatted(i), "Modification d'un parametre", "Portee : TOUT"))
                .toList();
    }
}
