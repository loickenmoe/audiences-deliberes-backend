package com.afriland.audiences.common.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;

import java.util.Calendar;

/**
 * Sets PDF document properties (title, author, producer), so files are identifiable in file
 * explorers, mail clients and document systems without being opened.
 */
public final class MetadonneesPdf {

    /** Producer, shared by all documents: names the application, not the PDF library. */
    private static final String PRODUCTEUR = "Afriland First Bank - Audiences et deliberes";

    private MetadonneesPdf() {
    }

    /**
     * @param titre  what the document is, as one would name it
     * @param auteur requester; {@code null} when unknown
     */
    public static void appliquer(PDDocument document, String titre, String auteur) {
        PDDocumentInformation infos = document.getDocumentInformation();
        infos.setTitle(TextePdf.replier(titre));
        infos.setProducer(PRODUCTEUR);
        infos.setCreator(PRODUCTEUR);
        if (auteur != null && !auteur.isBlank()) {
            infos.setAuthor(TextePdf.replier(auteur));
        }
        infos.setCreationDate(Calendar.getInstance());
    }
}
