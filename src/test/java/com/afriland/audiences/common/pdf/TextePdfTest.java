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
 * Le repli de texte, éprouvé sur ce qu'il promet (A1).
 *
 * <p>Deux exigences, de poids inégal. La première est <b>absolue</b> : quoi qu'on lui donne, le
 * résultat doit s'écrire dans un PDF sans lever — c'est la raison d'être de la classe, et un contrat
 * qu'on ne vérifie pas en relisant le code mais en l'écrivant vraiment. La seconde tient à la
 * <b>qualité du repli</b> : remplacer par un point d'interrogation est toujours correct, rarement
 * utile ; « Nguyễn » doit rester lisible.
 */
class TextePdfTest {

    /** Écrit réellement le texte dans un PDF : le seul juge du contrat « ne lève jamais ». */
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
        // Ils sont dans l'encodage : les remplacer serait une perte gratuite.
        assertThat(TextePdf.replier("Créé à Yaoundé, coût élevé")).isEqualTo("Créé à Yaoundé, coût élevé");
    }

    @Test
    @DisplayName("un caractere hors encodage garde sa lettre de base plutot que de disparaitre")
    void decompositionPlutotQuePointDInterrogation() {
        assertThat(TextePdf.replier("Nguyễn")).isEqualTo("Nguyen");
        assertThat(TextePdf.replier("ĀŹ")).isEqualTo("AZ");
    }

    /**
     * Ce qui n'a aucune lettre de base — un idéogramme, un emoji — devient un point d'interrogation.
     * C'est le seul cas où l'information est perdue, et il vaut mieux qu'une exception.
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

    /** QF-48 : le cadratin n'a pas sa place dans ce que lit le métier, fichiers compris. */
    @Test
    @DisplayName("le cadratin devient un trait d'union (QF-48)")
    void cadratinReplie() {
        assertThat(TextePdf.replier("audit — du 1er au 31")).isEqualTo("audit - du 1er au 31");
    }
}
