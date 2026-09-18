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
     * La colonne latérale, dans l'ordre où elle se lit sur le papier de la banque. Une chaîne vide
     * marque une respiration entre deux groupes ; elle ne s'imprime pas, elle espace.
     */
    public static final List<String> COLONNE = List.of(
            "S.A. au Capital de",
            "F.CFA 50 000 000 000",
            "RC N° : 87R041",
            "Contribuable",
            "M 1087000000 43 E",
            "",
            "Agrément Banque",
            "00005/MINFI/DCE2",
            "du 20 juillet 1987",
            "",
            "SWIFT/BIC",
            "CCEI CMCX",
            "",
            "Intermédiaire MAF :",
            "N° COSUMAF-LMFAC-01/2015",
            "PSI : 08/013/CMF/03",
            "",
            "Siège Social :",
            "1063, Place de",
            "l'indépendance",
            "Yaoundé-Cameroun",
            "",
            "B.P. 11834",
            "",
            "Tél. : +237 222 233 068",
            "        +237 222 223 734",
            "        +237 222 225 837",
            "        +237 679 529 970",
            "Fax : +237 222 221 785",
            "",
            "E-mail :",
            "firstbank@afrilandfirstbank.com",
            "",
            "Site web :",
            "www.afrilandfirstbank.com");
}
