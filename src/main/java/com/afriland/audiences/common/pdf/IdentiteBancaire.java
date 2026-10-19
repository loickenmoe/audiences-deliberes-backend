package com.afriland.audiences.common.pdf;

import java.util.List;

/**
 * Institutional mentions printed on the letterhead.
 *
 * <p>Kept in source control rather than environment variables: a missing variable would disappear
 * silently (a letter sent without its licence or registration number), and changes to legal mentions
 * must be versioned and reviewed.
 *
 * <p>These mentions appear on correspondence only. On a calendar export or audit archive they would
 * make a working document look like an official bank communication.
 */
public final class IdentiteBancaire {

    private IdentiteBancaire() {
    }

    public static final String ORGANISATION = "Afriland First Bank";

    /**
     * A line of the side column: text with its weight, a spacer, or a rule. As on the bank's
     * letterhead, the label is regular and the value bold.
     */
    public record Mention(String texte, boolean gras, boolean barre) {
    }

    private static Mention maigre(String texte) {
        return new Mention(texte, false, false);
    }

    private static Mention grasse(String texte) {
        return new Mention(texte, true, false);
    }

    /** Spacer between two groups; prints nothing. */
    private static final Mention ESPACE = new Mention("", false, false);

    /**
     * Separator rule. The bank's letterhead has exactly three: after the capital block, after SWIFT,
     * and after the MAF intermediary. Other groups are separated by whitespace only.
     */
    private static final Mention BARRE = new Mention("", false, true);

    /** The side column, in reading order on the bank's letterhead. */
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
