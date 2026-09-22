package com.afriland.audiences.common.pdf;

import java.util.List;

/**
 * Les mentions institutionnelles portées par le papier à en-tête (Q-115 B1).
 *
 * <p><b>Pourquoi elles vivent ici, dans le dépôt, et non en variables d'environnement.</b> C'était la
 * proposition initiale, et elle était mauvaise. Une variable non renseignée disparaît **en silence** :
 * une lettre partirait chez un huissier sans son agrément ni son RC, sans que personne ne soit
 * averti. Et une valeur en {@code .env} n'est versionnée nulle part — le jour où le capital change,
 * on ne saurait ni qui l'a modifié, ni quand. Pour un numéro d'agrément ministériel, cette
 * traçabilité n'est pas un luxe.
 *
 * <p>Ici, une modification est une ligne de diff qui passe par la relecture ordinaire du code. Pour
 * une mention légale, c'est une garantie, pas une lourdeur. Et ces valeurs ne changent pas : le RC
 * {@code 87R041} et l'agrément du 20 juillet 1987 sont acquis depuis quarante ans.
 *
 * <p><b>Ces mentions ne figurent que sur la correspondance.</b> Les porter sur un export de
 * calendrier ou une archive d'audit ferait passer un document de travail pour une communication
 * officielle de la banque — c'est la distinction posée en A2.
 */
public final class IdentiteBancaire {

    private IdentiteBancaire() {
    }

    public static final String ORGANISATION = "Afriland First Bank";

    /**
     * Une ligne de la colonne latérale : un texte et sa graisse, une respiration, ou une barre.
     *
     * <p>La graisse n'est pas décorative : sur le papier de la banque, <b>l'intitulé est maigre et
     * la valeur grasse</b> — « Agrément Banque » puis <b>00005/MINFI/DCE2</b>. C'est ce qui rend la
     * colonne lisible d'un coup d'œil, et c'est relevé sur le gabarit, non inventé.
     */
    public record Mention(String texte, boolean gras, boolean barre) {
    }

    private static Mention maigre(String texte) {
        return new Mention(texte, false, false);
    }

    private static Mention grasse(String texte) {
        return new Mention(texte, true, false);
    }

    /** Une respiration entre deux groupes : elle espace, elle ne s'imprime pas. */
    private static final Mention ESPACE = new Mention("", false, false);

    /**
     * Une barre de séparation. Le papier de la banque en porte <b>exactement trois</b>, relevées au
     * pixel : après le bloc du capital, après le SWIFT, et après l'intermédiaire MAF. Les autres
     * groupes ne sont séparés que par du blanc.
     */
    private static final Mention BARRE = new Mention("", false, true);

    /** La colonne latérale, dans l'ordre où elle se lit sur le papier de la banque. */
    public static final List<Mention> COLONNE = List.of(
            maigre("S.A. au Capital de"),
            grasse("F.CFA 50 000 000 000"),
            grasse("RC N° : 87R041"),
            maigre("Contribuable"),
            grasse("M 1087000000 43 E"),
            BARRE,
            maigre("Agrément Banque"),
            grasse("00005/MINFI/DCE2"),
            maigre("du 20 juillet 1987"),
            ESPACE,
            maigre("SWIFT/BIC"),
            grasse("CCEI CMCX"),
            BARRE,
            maigre("Intermédiaire MAF :"),
            maigre("N° COSUMAF-LMFAC-01/2015"),
            grasse("PSI : 08/013/CMF/03"),
            BARRE,
            maigre("Siège Social :"),
            grasse("1063, Place de"),
            grasse("l'indépendance"),
            grasse("Yaoundé-Cameroun"),
            ESPACE,
            grasse("B.P. 11834"),
            ESPACE,
            grasse("Tél. : +237 222 233 068"),
            grasse("        +237 222 223 734"),
            grasse("        +237 222 225 837"),
            grasse("        +237 679 529 970"),
            grasse("Fax : +237 222 221 785"),
            ESPACE,
            maigre("E-mail :"),
            grasse("firstbank@afrilandfirstbank.com"),
            ESPACE,
            maigre("Site web :"),
            grasse("www.afrilandfirstbank.com"));
}
