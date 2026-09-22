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
 * La provenance portée par le fichier lui-même (Q-114).
 *
 * <p><b>Ce que ces tests gardent.</b> Un fichier se renomme, se transfère, se retrouve dans une pièce
 * jointe sans son contexte. Ce qui n'est pas <em>dans</em> le document est perdu au premier
 * déplacement — et une archive dont on ne sait plus ce qu'elle couvre ni qui l'a produite ne vaut
 * plus grand-chose comme pièce. On vérifie donc que la référence et la période survivent au fichier
 * seul, dans <b>les deux formats</b> : le PDF qu'on remet comme l'Excel qu'on trie.
 *
 * <p>On ne vérifie pas la mise en page : un test ne sait pas si un document est lisible.
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
            // Et l'explication de ce qu'est l'empreinte : sans elle, celui qui reçoit la pièce
            // ignore qu'il peut la vérifier, et l'empreinte ne sert alors à personne.
            assertThat(texte).contains("SHA-256");
            // La charte commune, que l'archive n'avait pas jusqu'ici (A3).
            assertThat(texte).contains("Afriland First Bank").contains("Direction Juridique");
            assertThat(texte).contains("Page 1 sur");
        }
    }

    @Test
    @DisplayName("le classeur porte la même provenance, sur une feuille qui ne gêne pas la lecture")
    void excelPorteSaProvenance() throws Exception {
        byte[] contenu = renderer.excel(deuxLignes(), DEBUT, FIN, TITRE, "Paule Directrice");

        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(contenu))) {
            // Les lignes d'abord : celui qui ouvre le classeur vient pour elles.
            assertThat(classeur.getSheetAt(0).getSheetName()).isEqualTo("Audit");
            assertThat(classeur.getSheetAt(0).getLastRowNum()).isEqualTo(2); // en-tête + 2 lignes

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
     * Un auteur absent ne doit pas produire « Extrait par : null ». Le cas se présente pour une
     * extraction déclenchée par un traitement, et « null » dans une pièce transmise fait mauvais
     * effet là où un tiret ne dit rien de plus que l'absence.
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
     * Le colophon doit rester la **dernière** page (A3).
     *
     * <p>Il ferme la pièce : le placer ailleurs le ferait prendre pour un préambule, et celui qui
     * feuillette s'arrêterait avant de savoir comment vérifier ce qu'il tient.
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
             * Et la dernière page ne porte aucune ligne d'audit : le colophon y est seul. On vise une
             * référence **complète** : « D-2026- » seul est contenu dans « AUD-2026-0042 », qui figure
             * au titre de chaque page — l'assertion aurait échoué sur le titre, pas sur une donnée.
             */
            assertThat(derniere).doesNotContain("D-2026-100").doesNotContain("Modification d'un parametre");
        }
    }

    /**
     * Sur mille cinq cents lignes, perdre les en-têtes en page 2 rendrait les colonnes indéchiffrables
     * — et c'est précisément ce qu'un contrôleur lit.
     */
    @Test
    @DisplayName("les en-têtes de colonnes sont répétés sur chaque page de lignes")
    void entetesRepetes() throws Exception {
        byte[] contenu = renderer.pdf(beaucoupDeLignes(200), DEBUT, FIN, TITRE, "Paule Directrice");

        try (PDDocument document = Loader.loadPDF(contenu)) {
            String texte = new PDFTextStripper().getText(document);
            long avecEntete = texte.lines().filter(l -> l.contains("Horodatage") || l.contains("Auteur")).count();
            // Toutes les pages sauf le colophon.
            assertThat(avecEntete).isEqualTo(document.getNumberOfPages() - 1L);
        }
    }

    /**
     * La colonne « Détails » coupait ce qui dépassait sa largeur, remplacé par « ... ». Un détail
     * long doit désormais figurer en entier, replié sur plusieurs lignes — y compris un mot plus
     * large que la colonne, comme une empreinte.
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
        // L'empreinte est plus large que la colonne : coupée au caractère, elle doit se retrouver
        // entière une fois les retours à la ligne ôtés.
        assertThat(texte.replaceAll("\\s", "")).contains(empreinte);
    }

    /** Aucune ligne ne doit se perdre en franchissant une page. */
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
