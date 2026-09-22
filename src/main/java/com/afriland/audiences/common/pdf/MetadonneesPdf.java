package com.afriland.audiences.common.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;

import java.util.Calendar;

/**
 * Renseigne les propriétés d'un document PDF (A1).
 *
 * <p><b>Ce que cela corrige.</b> Aucun des quatre documents du système ne portait de métadonnées.
 * Dans l'explorateur de fichiers de celui qui les reçoit, dans une messagerie, dans un moteur
 * documentaire, ils apparaissaient tous sans titre ni origine — quatre fichiers anonymes qu'il
 * fallait ouvrir pour savoir ce qu'ils étaient.
 *
 * <p>Rien de tout cela n'est visible sur la page : c'est précisément pourquoi cela se perd, et
 * pourquoi personne ne le réclame.
 */
public final class MetadonneesPdf {

    /**
     * Le producteur, identique pour les quatre documents. Il désigne l'application, pas la
     * bibliothèque : « PDFBox » ne dit rien à qui reçoit une lettre de constitution.
     */
    private static final String PRODUCTEUR = "Afriland First Bank - Audiences et deliberes";

    private MetadonneesPdf() {
    }

    /**
     * @param titre  ce que le document est, tel qu'on le nommerait à l'oral
     * @param auteur qui l'a demandé ; {@code null} quand le générateur ne le sait pas encore —
     *               l'attribution deviendra systématique avec la charte (A2)
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
