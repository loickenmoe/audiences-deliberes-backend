package com.afriland.audiences.common.pdf;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * PDF document texts in the requested language, so adding a language only requires a new bundle
 * file. French is the default: it is the language of the procedure, and formal documents
 * (appointment letter, audit archive) always stay in French.
 */
public final class LibellesPdf {

    private static final String BASE = "pdf.libelles";

    private final ResourceBundle bundle;

    private LibellesPdf(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    /** @param langue requested language; {@code null} means French */
    public static LibellesPdf pour(Locale langue) {
        return new LibellesPdf(ResourceBundle.getBundle(BASE, langue == null ? Locale.FRENCH : langue));
    }

    /**
     * Text for a key. No encoding conversion: since Java 9, {@code PropertyResourceBundle} reads
     * {@code .properties} files as UTF-8 (falling back to ISO-8859-1 only on decoding failure).
     */
    public String texte(String cle) {
        return bundle.getString(cle);
    }

    /** Text for a key with its parameters substituted ({@code {0}}, {@code {1}}...). */
    public String texte(String cle, Object... parametres) {
        return MessageFormat.format(texte(cle), parametres);
    }
}
