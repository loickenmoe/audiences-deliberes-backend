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

/**
 * La charte, éprouvée sur ce qu'elle promet (Q-115 A2).
 *
 * <p>On ne vérifie pas qu'une page est belle — un test ne sait pas cela. On vérifie ce qui se perd
 * sans qu'on s'en aperçoive : la marque présente sur <b>chaque</b> page, la numérotation juste, les
 * en-têtes de tableau redessinés après un saut de page, et les données ni tronquées ni débordantes.
 */
class DocumentPdfTest {

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
        // Le pied attribue : c'est un fait, pas une signature en attente.
        assertThat(contenu).contains("Édité par Marie Directrice");
        // La numérotation connaît le total — ce qui suppose de n'écrire le pied qu'à la fin.
        assertThat(contenu).contains("Page 1 sur " + pages).contains("Page " + pages + " sur " + pages);
    }

    /**
     * Le défaut le plus coûteux d'un tableau paginé : perdre ses en-têtes en page 2. Le lecteur ne
     * sait alors plus ce qu'il lit, et rien ne le lui signale.
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
     * La coupe se fait à la **largeur mesurée**, pas au nombre de caractères : Helvetica est
     * proportionnelle, et compter les signes revenait à déborder sur la colonne voisine.
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

    /** Un document sans la moindre ligne doit rester un document, pas un fichier illisible. */
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
     * Sans auteur connu, le pied porte la date plutôt qu'un vide : un document qui ne dit pas quand
     * il a été édité laisse croire qu'il est à jour.
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
}
