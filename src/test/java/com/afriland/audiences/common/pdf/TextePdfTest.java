package com.afriland.audiences.common.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The text fallback, tested against what it promises.
 *
 * <p>Two requirements of unequal weight. The first is <b>absolute</b>: whatever the input, the
 * result must be writable to a PDF without throwing. The second is the <b>quality</b> of the
 * fallback: a question mark is always correct but rarely useful; "Nguyễn" must stay readable.
 */
class TextePdfTest {

    /** Actually writes the text to a PDF: the only judge of the "never throws" contract. */
    private static void ecrireDansUnPdf(String texte) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.beginText();
                cs.newLineAtOffset(50, 700);
                cs.showText(TextePdf.replier(texte));
                cs.endText();
            }
        }
    }

    @ParameterizedTest(name = "s''ecrit sans lever : {0}")
    @ValueSource(strings = {
            "Deux lignes\nsepareees par un retour",
            "Une\ttabulation",
            "Espace insecable et espace fine",
            "Nom vietnamien : Nguyễn",
            "Grec : αβγ",
            "Emoji : 📄",
            "Cyrillique : Ж",
            "Arabe : م",
            "Guillemets « typographiques » et apostrophe ’",
            "Retour chariot Windows\r\nsur deux lignes",
    })
    void toutTexteSEcritSansLever(String texte) {
        assertThatCode(() -> ecrireDansUnPdf(texte)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un texte vide ou absent ne casse rien")
    void videEtNul() {
        assertThat(TextePdf.replier(null)).isEmpty();
        assertThat(TextePdf.replier("")).isEmpty();
    }

    @Test
    @DisplayName("les accents francais sont conserves, pas aplatis")
    void accentsPreserves() {
        // They are in the encoding: replacing them would be a needless loss.
        assertThat(TextePdf.replier("Créé à Yaoundé, coût élevé")).isEqualTo("Créé à Yaoundé, coût élevé");
    }

    @Test
    @DisplayName("un caractere hors encodage garde sa lettre de base plutot que de disparaitre")
    void decompositionPlutotQuePointDInterrogation() {
        assertThat(TextePdf.replier("Nguyễn")).isEqualTo("Nguyen");
        assertThat(TextePdf.replier("ĀŹ")).isEqualTo("AZ");
    }

    /**
     * Anything with no base letter (an ideogram, an emoji) becomes a question mark: the only case where
     * information is lost, and better than an exception.
     */
    @Test
    @DisplayName("ce qui n'a pas de lettre de base devient un point d'interrogation")
    void sansEquivalentDevientInterrogation() {
        assertThat(TextePdf.replier("中")).isEqualTo("?");
    }

    @Test
    @DisplayName("les sauts de ligne deviennent des espaces, le texte reste continu")
    void sautsDeLigneEnEspaces() {
        assertThat(TextePdf.replier("Premiere ligne\nSeconde ligne")).isEqualTo("Premiere ligne Seconde ligne");
        assertThat(TextePdf.replier("Windows\r\nligne")).isEqualTo("Windows  ligne");
    }

    /** The em dash has no place in business-facing text, files included. */
    @Test
    @DisplayName("le cadratin devient un trait d'union (QF-48)")
    void cadratinReplie() {
        assertThat(TextePdf.replier("audit — du 1er au 31")).isEqualTo("audit - du 1er au 31");
    }
}
