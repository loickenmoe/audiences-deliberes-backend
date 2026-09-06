package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.intervenant.domain.DemandeConstitution;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;

/** Génère la lettre de constitution (RG-INT-08, FR-INT-07/US 5.7) validée par le SH — PDFBox (cf. Q-31). */
@Service
public class LettreConstitutionService {

    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] generer(DemandeConstitution demande) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float x = 60, y = page.getMediaBox().getHeight() - 80;
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                y = ecrireLigne(cs, x, y, "LETTRE DE CONSTITUTION");
                y -= 20;
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                y = ecrireLigne(cs, x, y, "Dossier : " + demande.getDossier().getReference());
                y = ecrireLigne(cs, x, y, "Prestataire constitué : " + demande.getPrestataire().getNom());
                y = ecrireLigne(cs, x, y, "Motif : " + demande.getMotif());
                y = ecrireLigne(cs, x, y, "Validée par : " + demande.getValidePar().getNom() + " " + demande.getValidePar().getPrenom());
                ecrireLigne(cs, x, y, "Date : " + demande.getDateDecision().toLocalDate().format(FORMAT_DATE));
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Échec de la génération de la lettre de constitution", e);
        }
    }

    private float ecrireLigne(PDPageContentStream cs, float x, float y, String texte) throws IOException {
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(texte);
        cs.endText();
        return y - 20f;
    }
}
