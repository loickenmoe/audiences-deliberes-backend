package com.afriland.audiences.reporting.service;

import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.pdf.LecturePdf;
import com.afriland.audiences.reporting.web.dto.RapportActiviteResponse;
import com.afriland.audiences.reporting.web.dto.RapportActiviteResponse.LigneActivite;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Activity sheet exports: one row per action type in service order, the average report delay last
 * after a blank row and only when it exists, and a file name telling the person and period apart.
 */
class RapportActiviteExportServiceTest {

    private static final LocalDate DEBUT = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 9, 30);

    private final RapportActiviteExportService service = new RapportActiviteExportService();

    private static RapportActiviteResponse fiche(String nom, Double delai) {
        return new RapportActiviteResponse(4L, nom, "JURISTE", DEBUT, FIN, List.of(
                new LigneActivite(TypeActionHistorique.DOSSIER_CREE, 3),
                new LigneActivite(TypeActionHistorique.CONDAMNATION_STATUT_MODIFIE, 1)), delai);
    }

    private static String libelle(TypeActionHistorique code) {
        return code.libelle();
    }

    @Test
    void csv_uneLigneParAction_puisLeDelaiMoyen() {
        String csv = new String(service.exportCsv(fiche("Jean Juriste", 2.5)), StandardCharsets.UTF_8);

        assertThat(csv.split("\n")).containsExactly(
                "Nature de l'action;Nombre",
                libelle(TypeActionHistorique.DOSSIER_CREE) + ";3",
                libelle(TypeActionHistorique.CONDAMNATION_STATUT_MODIFIE) + ";1",
                ";",
                "Délai moyen de saisie d'un compte rendu;" + String.format("%.1f j", 2.5));
    }

    @Test
    void csv_sansCompteRendu_neMontrePasDeDelai() {
        String csv = new String(service.exportCsv(fiche("Jean Juriste", null)), StandardCharsets.UTF_8);

        assertThat(csv).doesNotContain("Délai moyen");
        assertThat(csv.split("\n")).hasSize(3);
    }

    @Test
    void excel_memesLignesQueLeCsv() throws IOException {
        byte[] octets = service.exportExcel(fiche("Jean Juriste", 1.0));

        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(octets))) {
            Sheet feuille = classeur.getSheet("Activité");
            assertThat(feuille.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Nature de l'action");
            assertThat(feuille.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Nombre");
            assertThat(feuille.getRow(1).getCell(0).getStringCellValue()).isEqualTo(libelle(TypeActionHistorique.DOSSIER_CREE));
            assertThat(feuille.getRow(1).getCell(1).getStringCellValue()).isEqualTo("3");
            assertThat(feuille.getRow(4).getCell(0).getStringCellValue()).isEqualTo("Délai moyen de saisie d'un compte rendu");
            assertThat(feuille.getLastRowNum()).isEqualTo(4);
        }
    }

    @Test
    void pdf_titreAuNomDeLaPersonne_etPeriode() throws Exception {
        String texte = LecturePdf.texte(service.exportPdf(fiche("Jean Juriste", 2.0)));

        assertThat(texte).contains("Activité de Jean Juriste")
                .contains("Du 01/09/2026 au 30/09/2026")
                .contains(libelle(TypeActionHistorique.DOSSIER_CREE))
                .contains("Délai moyen de saisie d'un compte rendu");
    }

    @Test
    void pdf_personneSansNom_designeeParSonIdentifiant() throws Exception {
        String texte = LecturePdf.texte(service.exportPdf(fiche(null, null)));

        assertThat(texte).contains("Activité de l'utilisateur #4").doesNotContain("Délai moyen");
    }

    @Test
    void nomFichier_sansAccentNiEspace_ouIdentifiantAdefautDeNom() {
        assertThat(service.nomFichier(fiche("Éloïse N'Dongo-Kana ", null), "pdf"))
                .isEqualTo("activite-eloise-n-dongo-kana-2026-09-01_2026-09-30.pdf");
        assertThat(service.nomFichier(fiche(null, null), "csv")).isEqualTo("activite-4-2026-09-01_2026-09-30.csv");
    }
}
