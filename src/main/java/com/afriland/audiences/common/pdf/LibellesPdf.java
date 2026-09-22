package com.afriland.audiences.common.pdf;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Les textes des documents PDF, dans la langue demandée (A2).
 *
 * <p><b>Pourquoi une {@link Locale} dès maintenant, alors qu'on ne sert que le français.</b> Le pied
 * de page et les en-têtes de colonnes sont du texte : les écrire en dur reviendrait à rouvrir les
 * quatre générateurs le jour où la langue arrive. Le jalon C n'aura qu'un fichier à poser à côté de
 * celui-ci, et le branchement de {@code Accept-Language} à faire.
 *
 * <p><b>Le français reste le défaut, et ce n'est pas un repli technique</b> : c'est la langue de la
 * procédure. Une pièce — lettre de constitution, archive d'audit — y reste quoi qu'il arrive ; seuls
 * les documents de travail suivent leur lecteur.
 */
public final class LibellesPdf {

    private static final String BASE = "pdf.libelles";

    private final ResourceBundle bundle;

    private LibellesPdf(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    /** @param langue langue demandée ; {@code null} vaut français, la langue de la procédure */
    public static LibellesPdf pour(Locale langue) {
        return new LibellesPdf(ResourceBundle.getBundle(BASE, langue == null ? Locale.FRENCH : langue));
    }

    /**
     * Le texte associé à une clé.
     *
     * <p><b>Aucune conversion d'encodage ici.</b> Depuis Java 9, {@code PropertyResourceBundle} lit
     * les fichiers {@code .properties} en <b>UTF-8</b> et ne retombe sur ISO-8859-1 que si le
     * décodage échoue. Une première version « rétablissait » l'encodage à la main : elle produisait
     * « ?dit? par » dans les pieds de page, et seuls les libellés sans accent en réchappaient. Le
     * test l'a montré avant que le premier document ne sorte.
     */
    public String texte(String cle) {
        return bundle.getString(cle);
    }

    /** Le texte associé à une clé, ses paramètres substitués ({@code {0}}, {@code {1}}…). */
    public String texte(String cle, Object... parametres) {
        return MessageFormat.format(texte(cle), parametres);
    }
}
