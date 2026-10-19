package com.afriland.audiences.common.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * A tiny but valid PNG: this test checks geometry, not a drawing. PDFBox only needs to accept it
 * for the reserved band to be filled on one side and empty on the other.
 */

/**
 * The document template, tested against what it promises.
 *
 * <p>Not whether a page looks good, but what gets lost unnoticed: branding on <b>every</b> page,
 * correct numbering, table headers redrawn after a page break, and data neither truncated nor
 * overflowing.
 */
class DocumentPdfTest {
    private static final byte[] PNG_VALIDE = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");


    private static final List<String> ENTETES = List.of("Référence", "Juridiction", "Client");
    private static final float[] LARGEURS = {120f, 160f, 200f};

    private static List<List<String>> lignes(int nombre) {
        return IntStream.range(0, nombre)
                .mapToObj(i -> List.of("DOS-2026-%03d".formatted(i), "Tribunal de Yaoundé", "Client " + i))
                .map(l -> (List<String>) l)
                .toList();
    }

    private static String texte(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(document);
        }
    }

    private static int nombreDePages(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    @Test
    @DisplayName("chaque page porte la marque, le titre et son numéro")
    void chaquePagePorteLaMarque() throws Exception {
        byte[] pdf;
        try (DocumentPdf doc = new DocumentPdf(DocumentPdf.paysage(),
                "Calendrier des audiences", "Du 2026-09-01 au 2026-09-30", "Marie Directrice", Locale.FRENCH)) {
            doc.nouvellePage();
            doc.tableau(ENTETES, LARGEURS, lignes(120));
            pdf = doc.finaliser();
        }

        int pages = nombreDePages(pdf);
        assertThat(pages).as("120 lignes ne tiennent pas sur une page").isGreaterThan(1);

        String contenu = texte(pdf);
        assertThat(contenu).contains("Afriland First Bank").contains("Direction Juridique");
        assertThat(contenu).contains("Calendrier des audiences");
        assertThat(contenu).contains("Du 2026-09-01 au 2026-09-30");
        // The footer attributes: it states a fact, not a pending signature.
        assertThat(contenu).contains("Édité par Marie Directrice");
        // The numbering knows the total, which requires writing the footer last.
        assertThat(contenu).contains("Page 1 sur " + pages).contains("Page " + pages + " sur " + pages);
    }

    /**
     * The costliest defect of a paginated table: losing its headers on page 2, leaving the reader lost
     * with no warning.
     */
    @Test
    @DisplayName("les en-têtes de colonnes sont redessinés à chaque page")
    void entetesRepetesSurChaquePage() throws Exception {
        byte[] pdf;
        try (DocumentPdf doc = new DocumentPdf(DocumentPdf.paysage(), "Calendrier", null, null, Locale.FRENCH)) {
            doc.nouvellePage();
            doc.tableau(ENTETES, LARGEURS, lignes(120));
            pdf = doc.finaliser();
        }

        int pages = nombreDePages(pdf);
        long occurrences = texte(pdf).lines().filter(l -> l.contains("Juridiction")).count();
        assertThat(occurrences).isEqualTo(pages);
    }

    @Test
    @DisplayName("aucune ligne n'est perdue en franchissant les pages")
    void aucuneLignePerdue() throws Exception {
        byte[] pdf;
        try (DocumentPdf doc = new DocumentPdf(DocumentPdf.paysage(), "Calendrier", null, null, Locale.FRENCH)) {
            doc.nouvellePage();
            doc.tableau(ENTETES, LARGEURS, lignes(120));
            pdf = doc.finaliser();
        }

        String contenu = texte(pdf);
        assertThat(contenu).contains("DOS-2026-000").contains("DOS-2026-059").contains("DOS-2026-119");
    }

    /**
     * Wrapping uses the <b>measured width</b>, not a character count: Helvetica is proportional, and
     * counting characters overflows into the next column.
     */
    @Test
    @DisplayName("une valeur trop longue est coupée, jamais laissée à déborder")
    void valeurTropLongueCoupee() throws Exception {
        byte[] pdf;
        try (DocumentPdf doc = new DocumentPdf(PDRectangle.A4, "Rapport", null, null, Locale.FRENCH)) {
            doc.nouvellePage();
            doc.tableau(List.of("Colonne"), new float[]{60f},
                    List.of(List.of("Une valeur beaucoup trop longue pour soixante points de large")));
            pdf = doc.finaliser();
        }

        String contenu = texte(pdf);
        assertThat(contenu).contains("...");
        assertThat(contenu).doesNotContain("soixante points de large");
    }

    /** A document with no rows must still be a valid document. */
    @Test
    @DisplayName("un document vide sort une page qui le dit")
    void documentVide() throws Exception {
        byte[] pdf;
        try (DocumentPdf doc = new DocumentPdf(PDRectangle.A4, "Calendrier", "Aucune audience", null, Locale.FRENCH)) {
            pdf = doc.finaliser();
        }

        assertThat(nombreDePages(pdf)).isEqualTo(1);
        assertThat(texte(pdf)).contains("Calendrier").contains("Page 1 sur 1");
    }

    /**
     * With no known author, the footer shows the date instead of nothing: a document that does not say
     * when it was produced suggests it is current.
     */
    @Test
    @DisplayName("sans auteur, le pied porte la date d'édition")
    void sansAuteurLePiedPorteLaDate() throws Exception {
        byte[] pdf;
        try (DocumentPdf doc = new DocumentPdf(PDRectangle.A4, "Rapport", null, null, Locale.FRENCH)) {
            doc.nouvellePage();
            doc.ligne("Un indicateur : 4", false, 10f);
            pdf = doc.finaliser();
        }

        assertThat(texte(pdf)).contains("Édité le").doesNotContain("Édité par");
    }

    @Test
    @DisplayName("le logo de la banque est embarqué dans le document")
    void logoEmbarque() throws Exception {
        byte[] pdf;
        try (DocumentPdf doc = new DocumentPdf(PDRectangle.A4, "Rapport", null, null, Locale.FRENCH)) {
            doc.nouvellePage();
            pdf = doc.finaliser();
        }

        try (PDDocument document = Loader.loadPDF(pdf)) {
            var ressources = document.getPage(0).getResources();
            assertThat(ressources.getXObjectNames()).as("une image en ressource de la page").isNotEmpty();
        }
    }

    /**
     * An unreadable image must not break the document.
     *
     * <p>The stored signature is a convenience; the appointment letter is an official document. PDFBox
     * decoders throw unchecked exceptions on malformed input, which must be caught.
     */
    @Test
    void deuxSignatures_ignoreUneImageIllisibleEtRendLeDocument() throws Exception {
        byte[] pdf;
        try (DocumentPdf doc = new DocumentPdf(PDRectangle.A4, "Lettre", null, null, Locale.FRENCH)) {
            doc.nouvellePage();
            doc.centre("La Direction Juridique", true, 10f);
            doc.deuxSignatures("ceci n'est pas une image".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "Clara Juriste", null, "Le Secrétaire Général", 42f);
            pdf = doc.finaliser();
        }

        // The document exists and carries both names: the letter is produced without the image.
        assertThat(LecturePdf.texte(pdf)).contains("Clara Juriste").contains("Le Secrétaire Général");
    }

    /** Nothing to affix: whoever has not uploaded a signature signs by hand, and the document is unaffected. */
    @Test
    void deuxSignatures_toleLAbsenceDImage() throws Exception {
        byte[] pdf;
        try (DocumentPdf doc = new DocumentPdf(PDRectangle.A4, "Lettre", null, null, Locale.FRENCH)) {
            doc.nouvellePage();
            doc.deuxSignatures(null, "Clara Juriste", new byte[0], "Henri Huissier", 42f);
            pdf = doc.finaliser();
        }

        assertThat(LecturePdf.texte(pdf)).contains("Clara Juriste").contains("Henri Huissier");
    }

    /**
     * Both names stay on the <b>same line</b>, whether there are one or two signatures. Not having
     * uploaded an image is a normal case, and the letter must not look lopsided.
     */
    @Test
    void deuxSignatures_alignentLesNomsMemeSansImage() throws Exception {
        byte[] avecUneImage;
        byte[] sansAucuneImage;
        try (DocumentPdf doc = new DocumentPdf(PDRectangle.A4, "Lettre", null, null, Locale.FRENCH)) {
            doc.nouvellePage();
            doc.deuxSignatures(PNG_VALIDE, "Clara Juriste", null, "Henri Huissier", 42f);
            avecUneImage = doc.finaliser();
        }
        try (DocumentPdf doc = new DocumentPdf(PDRectangle.A4, "Lettre", null, null, Locale.FRENCH)) {
            doc.nouvellePage();
            doc.deuxSignatures(null, "Clara Juriste", null, "Henri Huissier", 42f);
            sansAucuneImage = doc.finaliser();
        }

        // The reserved band is the same: the names land in the same place in both cases.
        assertThat(LecturePdf.positions(avecUneImage)).isEqualTo(LecturePdf.positions(sansAucuneImage));
    }
}
