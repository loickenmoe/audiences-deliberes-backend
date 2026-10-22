package com.afriland.audiences.common.pdf;

import java.text.Normalizer;

/**
 * Makes any text writable with the standard PDF fonts (Helvetica, {@code WinAnsiEncoding}).
 *
 * <p>PDFBox throws, rather than substituting, on any character outside that encoding, and documents
 * copy free user input (line breaks, tabs, narrow no-break spaces, letters such as "ễ"). Since the
 * appointment letter is generated inside the validation transaction, such a failure would roll back
 * the validation itself.
 *
 * <p>Three steps, in order: control characters become a space; typography is folded to ASCII (the
 * em dash becomes a hyphen); remaining characters are decomposed, so "Nguyễn" becomes "Nguyen"
 * rather than a question mark.
 */
public final class TextePdf {

    /** No-break space. */
    private static final int ESPACE_INSECABLE = 0x00A0;
    /** Narrow no-break space, commonly pasted from word processors. */
    private static final int ESPACE_FINE_INSECABLE = 0x202F;

    private TextePdf() {
    }

    /**
     * Folds a text onto what the standard fonts can write.
     *
     * @return a writable text, never {@code null}
     */
    public static String replier(String texte) {
        if (texte == null || texte.isEmpty()) {
            return "";
        }
        String typographie = texte
                .replace('’', '\'').replace('‘', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('—', '-').replace('–', '-')
                .replace('…', '.');

        StringBuilder sortie = new StringBuilder(typographie.length());
        typographie.codePoints().forEach(point -> sortie.append(replierUnPoint(point)));
        return sortie.toString();
    }

    private static String replierUnPoint(int point) {
        /*
         * Line breaks, tabs, other controls and special spaces become a plain space. Special spaces
         * are referenced by code point because they would be invisible in the source.
         */
        if (Character.isISOControl(point) || point == ESPACE_INSECABLE || point == ESPACE_FINE_INSECABLE) {
            return " ";
        }
        if (estEcrivable(point)) {
            return String.valueOf(Character.toChars(point));
        }
        /*
         * Outside the encoding: decompose (NFD) and keep only writable components, so "ễ" becomes "e"
         * instead of a question mark.
         */
        String decompose = Normalizer.normalize(String.valueOf(Character.toChars(point)), Normalizer.Form.NFD);
        StringBuilder base = new StringBuilder(1);
        decompose.codePoints().filter(TextePdf::estEcrivable).forEach(base::appendCodePoint);
        return base.isEmpty() ? "?" : base.toString();
    }

    /**
     * True if {@code WinAnsiEncoding} can render this code point: printable ASCII, then upper
     * Latin-1. {@code U+007F} to {@code U+009F} are control characters and are rejected too.
     */
    private static boolean estEcrivable(int point) {
        return (point >= 0x20 && point <= 0x7E) || (point >= 0x00A1 && point <= 0x00FF);
    }
}
