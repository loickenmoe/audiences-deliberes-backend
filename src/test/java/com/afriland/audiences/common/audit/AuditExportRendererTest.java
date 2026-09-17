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
}
