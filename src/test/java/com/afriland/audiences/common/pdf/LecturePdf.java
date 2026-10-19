package com.afriland.audiences.common.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.util.List;
import java.util.Locale;

/**
 * Reads back a PDF produced by the system, for tests: checks what the file contains rather than only
 * that it is a non-empty byte array starting with {@code %PDF-}.
 */
public final class LecturePdf {

    private LecturePdf() {
    }

    /** The document text, all pages combined. */
    public static String texte(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    /**
     * The text <b>with its position</b>, one line per fragment: {@code x;y;text}, rounded to the point.
     *
     * <p>Unlike {@link #texte(byte[])}, this verifies that content lands in the <b>same place</b> from
     * one rendering to the next, which matters when printing on preprinted paper. Coordinates are
     * rounded because PDFBox computes them in floating point.
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

    /** File properties: invisible on the page, which is why they get lost. */
    public static PDDocumentInformation proprietes(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getDocumentInformation();
        }
    }
}
