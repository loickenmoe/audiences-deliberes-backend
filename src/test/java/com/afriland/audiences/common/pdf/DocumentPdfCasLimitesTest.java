package com.afriland.audiences.common.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Edge cases of the document template: what must neither be lost nor break the document when the
 * content is unusual (absent paragraph, text longer than a page, a single word wider than its
 * column, a row with fewer cells than columns, an oversized signature, no subtitle).
 */
class DocumentPdfCasLimitesTest {

    private static String texte(byte[] pdf) throws Exception {
        return LecturePdf.texte(pdf);
    }

    private static int pages(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    private static byte[] pngLarge() throws Exception {
        BufferedImage image = new BufferedImage(600, 20, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", sortie);
            return sortie.toByteArray();
        }
    }

    @Test
    void paragrapheAbsent_neLaisseRien_etUnLongParagraphe_continueSurLaPageSuivante() throws Exception {
        String paragrapheLong = String.join(" ", java.util.Collections.nCopies(1200, "audience")) + " FIN-DU-PARAGRAPHE";
        byte[] pdf;
        try (DocumentPdf doc = new DocumentPdf(PDRectangle.A4, "Note", "Sous-titre", "Auteur", Locale.FRENCH)) {
            doc.nouvellePage();
            doc.paragraphe(null, 10f);
            doc.paragraphe(paragrapheLong, 10f);
            pdf = doc.finaliser();
        }

        assertThat(pages(pdf)).isGreaterThan(1);
        assertThat(texte(pdf)).contains("FIN-DU-PARAGRAPHE");
    }

    @Test
    void lignesAlignees_aDroiteEtEnRetrait() throws Exception {
        byte[] pdf;
        try (DocumentPdf doc = new DocumentPdf(PDRectangle.A4, "Lettre", null, null, Locale.FRENCH)) {
            doc.nouvellePage();
            doc.ligneADroite("Douala, le 21 octobre 2026", 10f);
            doc.ligneEnRetrait("Maître Fotso", 250f, true, 10f);
            doc.ligneEnRetrait("Huissier de Justice", 250f, false, 10f);
            pdf = doc.finaliser();
        }

        assertThat(texte(pdf)).contains("Douala, le 21 octobre 2026").contains("Maître Fotso").contains("Huissier de Justice");
    }

    @Test
    void sansSousTitre_leTitreDuFichierEstLeTitreSeul() throws Exception {
        byte[] pdf;
        try (DocumentPdf doc = new DocumentPdf(PDRectangle.A4, "Rapport", " ", null, Locale.FRENCH)) {
            doc.nouvellePage();
            doc.ligne("Contenu", false, 10f);
            pdf = doc.finaliser();
        }

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getDocumentInformation().getTitle()).isEqualTo("Rapport");
        }
    }

    @Test
    void tableau_autantDeLargeursQueDeColonnes() {
        try (DocumentPdf doc = new DocumentPdf(PDRectangle.A4, "Tableau", null, null, Locale.FRENCH)) {
            doc.nouvellePage();
            float[] deuxLargeurs = {100f, 100f};
            List<String> troisColonnes = List.of("A", "B", "C");
            List<List<String>> aucuneLigne = List.of();

            assertThatThrownBy(() -> doc.tableau(troisColonnes, deuxLargeurs, aucuneLigne, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Autant de largeurs que de colonnes");
        }
    }

    @Test
    void tableauReplie_unMotPlusLargeQueSaColonneEstCoupeSansRienPerdre_etUneLigneCourteSeComplete() throws Exception {
        String empreinte = "a3f5".repeat(16);  // 64 characters, no space: a SHA-256 fingerprint
        byte[] pdf;
        try (DocumentPdf doc = new DocumentPdf(PDRectangle.A4, "Archive", null, null, Locale.FRENCH)) {
            doc.nouvellePage();
            doc.tableau(List.of("Empreinte", "Commentaire"), new float[]{60f, 200f},
                    List.of(List.of(empreinte, "ligne complète"), List.of("seule cellule")), true);
            pdf = doc.finaliser();
        }

        String contenu = texte(pdf).replaceAll("\\s", "");
        assertThat(contenu).contains(empreinte).contains("seulecellule");
    }

    @Test
    void tableauNonReplie_couperUneValeurTropLongue() throws Exception {
        byte[] pdf;
        try (DocumentPdf doc = new DocumentPdf(PDRectangle.A4, "Liste", null, null, Locale.FRENCH)) {
            doc.nouvellePage();
            doc.tableau(List.of("Client"), new float[]{40f},
                    List.of(List.of("Société Anonyme des Grandes Plantations du Littoral")));
            pdf = doc.finaliser();
        }

        assertThat(texte(pdf)).contains("...").doesNotContain("Grandes Plantations du Littoral");
    }

    @Test
    void signatureTropLarge_estReduitePourTenirDansSaColonne() throws Exception {
        byte[] pdf;
        try (DocumentPdf doc = new DocumentPdf(PDRectangle.A4, "Lettre", null, null, Locale.FRENCH)) {
            doc.nouvellePage();
            doc.deuxSignatures(pngLarge(), "Clara Juriste", new byte[0], " ", 42f);
            pdf = doc.finaliser();
        }

        // The wide image is affixed scaled down; the blank name on the right writes nothing.
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getPage(0).getResources().getXObjectNames()).isNotEmpty();
        }
        assertThat(texte(pdf)).contains("Clara Juriste");
    }
}
