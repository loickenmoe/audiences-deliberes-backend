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
        // Le taux est encore renvoyé par l'API, mais l'export ne le porte plus (2026-10-06) :
        // ce sont les trois décomptes qui doivent s'y retrouver.
        rapport = new RapportResponse(TypeRapport.MENSUEL, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                4L, 3L, 2L, 1L, 0.75d, 3L, 1L, 0L, 150000d);
    }

    @Test
    void exportPdf_produitUnPdfNonVide() {
        byte[] pdf = service.exportPdf(rapport);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    /** Les indicateurs sont-ils reellement dans le fichier, et pas seulement dans le CSV ? (A1) */
    @Test
    void exportPdf_contientLesIndicateurs() throws Exception {
        String texte = LecturePdf.texte(service.exportPdf(rapport));

        assertThat(texte).contains("MENSUEL").contains("2026-09-01").contains("2026-09-30");
        /*
         * Les indicateurs sont désormais présentés en **tableau** (A2) et non plus en « libellé :
         * valeur » : on vérifie donc que chaque valeur reste sur la ligne de son libellé, ce qui est
         * la seule chose qui compte et qui se lit encore dans le texte extrait.
         */
        assertThat(texte).containsPattern("Nouveaux dossiers\s+4");
        assertThat(texte).containsPattern("Audiences tenues\s+3");
        assertThat(texte).containsPattern("Litiges gagn.s\s+3");
        assertThat(texte).containsPattern("Litiges perdus\s+1");
        assertThat(texte).containsPattern("D.cisions mixtes\s+0");
        // Des nombres, pas un pourcentage (métier, 2026-10-06) : le taux a quitté l'export.
        assertThat(texte).doesNotContain("Taux de succ");
        // La marque est sur la page, et la page est numérotée.
        assertThat(texte).contains("Afriland First Bank").contains("Page 1 sur 1");
    }

    @Test
    void exportPdf_porteSesProprietes() throws Exception {
        // Le titre du fichier porte la portée, là où celui de la page s'en dispense : deux rapports
        // mensuels de mois différents ne doivent pas se confondre dans un explorateur (A2).
        assertThat(LecturePdf.proprietes(service.exportPdf(rapport)).getTitle())
                .isEqualTo("Rapport MENSUEL - Du 2026-09-01 au 2026-09-30");
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
        // Des nombres, pas un pourcentage : le métier lit l'export tel quel.
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

        assertThat(csv.lines()).hasSize(9); // en-tête + 8 indicateurs
        assertThat(vide.estVide()).isTrue();
    }
}
