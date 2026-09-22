package com.afriland.audiences.common.pdf;

import java.text.Normalizer;

/**
 * Rend un texte quelconque écrivable par les polices standard d'un PDF (A1).
 *
 * <p><b>Le problème.</b> Les quatre documents du système sont écrits en Helvetica, police standard
 * encodée en {@code WinAnsiEncoding}. PDFBox <b>lève une exception</b> — il ne remplace pas
 * silencieusement — dès qu'on lui demande d'écrire un caractère absent de cet encodage. Or nos
 * documents recopient des saisies libres : un motif de constitution, un nom de prestataire, un
 * libellé d'action. Mesuré sur la lettre de constitution, quatre saisies parfaitement légitimes
 * faisaient échouer la génération :
 *
 * <ul>
 *   <li>{@code U+000A} — la touche Entrée, le motif étant saisi dans une zone de texte ;</li>
 *   <li>{@code U+0009} — une tabulation, invisible à l'écran ;</li>
 *   <li>{@code U+202F} — l'espace fine insécable, que tout collage depuis un traitement de texte
 *       apporte avec lui ;</li>
 *   <li>{@code U+1EC5} — la lettre « ễ » d'un nom vietnamien.</li>
 * </ul>
 *
 * <p><b>Pourquoi cela dépassait la simple mise en forme.</b> La lettre de constitution est produite
 * <b>dans la transaction de validation</b> : l'exception annulait la validation elle-même. La
 * demande restait en attente, le prestataire n'était pas notifié, et comme le motif est enregistré,
 * chaque nouvelle tentative échouait à l'identique.
 *
 * <p><b>Trois gestes, dans cet ordre.</b> Les caractères de contrôle deviennent une espace — une
 * ligne de PDF ne se coupe pas toute seule, mieux vaut un texte continu qu'une exception. La
 * typographie est repliée sur l'ASCII, ce qui respecte au passage QF-48 (le cadratin devient un
 * trait d'union). Ce qui reste hors encodage est <b>décomposé</b> : « Nguyễn » s'écrit « Nguyen »,
 * lisible et reconnaissable, là où un point d'interrogation aurait effacé l'information.
 */
public final class TextePdf {

    /** Espace insécable. */
    private static final int ESPACE_INSECABLE = 0x00A0;
    /** Espace fine insécable — celle que tout collage depuis un traitement de texte apporte. */
    private static final int ESPACE_FINE_INSECABLE = 0x202F;

    private TextePdf() {
    }

    /**
     * Replie un texte sur ce que les polices standard savent écrire.
     *
     * @param texte texte d'origine, éventuellement {@code null}
     * @return un texte écrivable sans exception, jamais {@code null}
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
         * Sauts de ligne, tabulations, autres contrôles et espaces exotiques : une espace ordinaire,
         * jamais une exception. Écrites littéralement dans le source, ces espaces seraient invisibles
         * à la relecture : on les désigne par leur point de code.
         */
        if (Character.isISOControl(point) || point == ESPACE_INSECABLE || point == ESPACE_FINE_INSECABLE) {
            return " ";
        }
        if (estEcrivable(point)) {
            return String.valueOf(Character.toChars(point));
        }
        /*
         * Hors encodage : on décompose (NFD) et on ne garde que les composantes écrivables. « ễ »
         * devient « e » plutôt qu'un point d'interrogation. Un nom approximatif reste un nom ; un
         * nom effacé n'en est plus un.
         */
        String decompose = Normalizer.normalize(String.valueOf(Character.toChars(point)), Normalizer.Form.NFD);
        StringBuilder base = new StringBuilder(1);
        decompose.codePoints().filter(TextePdf::estEcrivable).forEach(c -> base.appendCodePoint(c));
        return base.isEmpty() ? "?" : base.toString();
    }

    /**
     * Vrai si {@code WinAnsiEncoding} sait rendre ce point de code.
     *
     * <p>L'intervalle utile se lit en deux morceaux : l'ASCII imprimable, puis le haut de Latin-1.
     * Entre les deux, {@code U+007F} à {@code U+009F} sont des caractères de contrôle — présents
     * dans l'intervalle « inférieur à 256 » qu'un premier repli avait retenu, mais refusés par la
     * police tout autant que les autres.
     */
    private static boolean estEcrivable(int point) {
        return (point >= 0x20 && point <= 0x7E) || (point >= 0x00A1 && point <= 0x00FF);
    }
}
