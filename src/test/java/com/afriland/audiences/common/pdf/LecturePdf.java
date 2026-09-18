package com.afriland.audiences.common.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

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

    /** Les propriétés du fichier — invisibles sur la page, et c'est pourquoi elles se perdent. */
    public static PDDocumentInformation proprietes(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getDocumentInformation();
        }
    }
}
