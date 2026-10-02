package com.afriland.audiences.common.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.util.List;
import java.util.Locale;

/**
 * Relit un PDF produit par le système, pour les tests (A1).
 *
 * <p><b>Pourquoi cette classe existe.</b> Les trois générateurs historiques n'étaient éprouvés que
 * par « le tableau d'octets n'est pas vide et commence par {@code %PDF-} ». Un document qui aurait
 * perdu toutes ses données passait au vert. Avant de refondre leur mise en forme, il fallait des
 * tests capables de dire ce que le fichier contient — sans quoi « ne rien casser » serait resté une
 * affirmation au lieu d'être une mesure.
 */
public final class LecturePdf {

    private LecturePdf() {
    }

    /** Le texte du document, toutes pages confondues. */
    public static String texte(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    /**
     * Le texte <b>avec sa position</b>, une ligne par fragment : {@code x;y;texte}, au point près.
     *
     * <p>Ce que cela permet de vérifier, et que {@link #texte(byte[])} laisse passer : qu'un même
     * contenu se pose au <b>même endroit</b> d'un tirage à l'autre. Un document dont le décor
     * change peut garder tous ses mots et les avoir tous déplacés — à l'impression sur une feuille
     * préimprimée, c'est la seule chose qui compte.
     *
     * <p>Les coordonnées sont arrondies au point : PDFBox les calcule en virgule flottante, et deux
     * rendus identiques peuvent différer d'un millième sans que rien n'ait bougé sur la page.
     */
    public static String positions(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            StringBuilder releve = new StringBuilder();
            PDFTextStripper releveur = new PDFTextStripper() {
                @Override
                protected void writeString(String texte, List<TextPosition> positions) {
                    if (positions.isEmpty() || texte.isBlank()) {
                        return;
                    }
                    TextPosition premiere = positions.get(0);
                    releve.append(String.format(Locale.ROOT, "%.0f;%.0f;%s%n",
                            premiere.getXDirAdj(), premiere.getYDirAdj(), texte.strip()));
                }
            };
            releveur.getText(document);
            return releve.toString();
        }
    }

    /** Les propriétés du fichier — invisibles sur la page, et c'est pourquoi elles se perdent. */
    public static PDDocumentInformation proprietes(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getDocumentInformation();
        }
    }
}
