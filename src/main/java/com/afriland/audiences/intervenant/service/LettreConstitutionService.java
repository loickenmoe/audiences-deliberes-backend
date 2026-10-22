package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.common.pdf.DocumentPdf;
import com.afriland.audiences.intervenant.domain.DemandeConstitution;
import com.afriland.audiences.intervenant.domain.LettreConstitution;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Composes and lays out the appointment letter (RG-INT-08, FR-INT-07, US 5.7) on the bank's
 * letterhead, following the legal department's template: number, recipient, subject, body,
 * signatory.
 *
 * <p>Empty fields render as dotted lines so the letter can be completed by hand. The request reason
 * is internal and deliberately not printed; the validator remains traceable in the file properties
 * and the document store.
 */
@Service
public class LettreConstitutionService {

    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Rendered for an empty field, as on the paper template. */
    private static final String POINTILLES = "..........................";

    /** Defaults taken from the legal department's template. */
    public static final String SUFFIXE_PAR_DEFAUT = "First Bank/DAJR/DR/CM/ABN/18";
    public static final String LIEU_PAR_DEFAUT = "Yaoundé";
    public static final String OBJET_PAR_DEFAUT = "Lettre de constitution contenant transmission de pièces.";
    /** Height reserved for each signature, in points (about 1.5 cm, a handwritten signature). */
    private static final float HAUTEUR_SIGNATURE = 42f;

    public static final String SIGNATAIRE_PAR_DEFAUT = "La Direction Juridique";
    public static final String QUALITE_PAR_DEFAUT = "Huissier de Justice";

    /**
     * Template body. Dotted lines mark what the drafter completes (seizure type, debtor, amount,
     * garnishee, attachments). None is pre-filled: the case client is not necessarily the targeted
     * debtor, and the risk amount is not the claim amount.
     */
    public static String corpsParDefaut() {
        return """
                Dans le cadre de notre collaboration et vous priant d'en faire une priorité ;
                Nous vous constituons par les présentes aux fins de pratiquer une saisie %1$s de créances \
                à l'encontre de %1$s, notre débiteur de la somme de F CFA %1$s (%1$s) ;

                Ladite saisie devra être pratiquée auprès de %1$s

                A cet effet, bien vouloir trouver en annexe %1$s
                En vous remerciant à l'avance de votre diligence, nous vous prions d'agréer Maître, \
                l'expression de nos sentiments distingués.""".formatted(POINTILLES);
    }

    /**
     * Draft for a request: known values filled in, the rest as dotted lines.
     *
     * @param numero {@code null} until signature, so abandoned requests leave no gaps in the series
     */
    public LettreConstitution brouillonPour(DemandeConstitution demande, Integer numero) {
        return LettreConstitution.builder()
                .demande(demande)
                .numero(numero)
                .suffixeNumero(SUFFIXE_PAR_DEFAUT)
                .lieu(LIEU_PAR_DEFAUT)
                .destinataireNom(demande.getPrestataire().getNom())
                .destinataireQualite(QUALITE_PAR_DEFAUT)
                .destinataireVille(LIEU_PAR_DEFAUT)
                .concerne("DOSSIER " + demande.getDossier().getReference())
                .objet(OBJET_PAR_DEFAUT)
                .corps(corpsParDefaut())
                .signataire(SIGNATAIRE_PAR_DEFAUT)
                .build();
    }

    /** Lays out the letter on the bank's letterhead: the authoritative copy archived in the GED. */
    public byte[] generer(LettreConstitution lettre) {
        return generer(lettre, false, null, null);
    }

    /**
     * Same letter with the stored signatures of the signatories. Without images, only the names are
     * printed and the letter is signed by hand; this is not an error.
     */
    public byte[] generer(LettreConstitution lettre, Signataire redacteur, Signataire valideur) {
        return generer(lettre, false, redacteur, valideur);
    }

    /**
     * @param pourPapierPreimprime print for the bank's pre-printed sheets: only the content is drawn,
     *                             at the same positions. This copy is not archived (a single copy
     *                             must be authoritative) and is regenerated on demand from the
     *                             stored letter.
     */
    public byte[] generer(LettreConstitution lettre, boolean pourPapierPreimprime) {
        return generer(lettre, pourPapierPreimprime, null, null);
    }

    /**
     * @param redacteur drafter, shown on the left under the department name
     * @param valideur  validator who commits the bank, shown on the right
     */
    public byte[] generer(LettreConstitution lettre, boolean pourPapierPreimprime,
                          Signataire redacteur, Signataire valideur) {
        String titre = "Lettre de constitution " + lettre.referenceComplete();
        Utilisateur validateur = lettre.getDemande() == null ? null : lettre.getDemande().getValidePar();
        try (DocumentPdf document = pourPapierPreimprime
                ? DocumentPdf.gabaritPreimprime(titre, nomComplet(validateur), Locale.FRENCH)
                : DocumentPdf.papierEnTete(titre, nomComplet(validateur), Locale.FRENCH)) {
            document.nouvellePage();

            // Number on the left, place and date on the right, on the same line as in the template.
            document.ligneGaucheEtDroite(lettre.referenceComplete(),
                    ou(lettre.getLieu(), LIEU_PAR_DEFAUT) + " le " + LocalDate.now().format(FORMAT_DATE),
                    10f);
            document.espace(18f);

            // Recipient block indented, as in a letter layout.
            document.ligneEnRetrait("Maître " + ou(lettre.getDestinataireNom(), POINTILLES), 150f, true, 10f);
            document.ligneEnRetrait(ou(lettre.getDestinataireQualite(), POINTILLES), 150f, true, 10f);
            document.ligneEnRetrait(ou(lettre.getDestinataireVille(), POINTILLES), 150f, true, 10f);
            document.espace(22f);

            document.etiquetteEtValeur("Concerne :", ou(lettre.getConcerne(), POINTILLES), 10f);
            document.espace(6f);
            document.etiquetteEtValeur("Objet:", ou(lettre.getObjet(), POINTILLES), 10f);
            document.espace(14f);

            document.paragraphe("Maître,", 10f);
            document.espace(4f);
            document.paragraphe(ou(lettre.getCorps(), corpsParDefaut()), 10f);
            document.espace(26f);

            /*
             * The issuing department, then below it the two people who commit the bank: drafter
             * on the left, validator on the right. Nothing is signed above the department line,
             * since it names a department, not a person. Without images, names are printed alone
             * for handwritten signature.
             */
            document.centre(ou(lettre.getSignataire(), SIGNATAIRE_PAR_DEFAUT), true, 10f);
            document.espace(20f);
            document.deuxSignatures(image(redacteur), nom(redacteur), image(valideur), nom(valideur),
                    HAUTEUR_SIGNATURE);
            return document.finaliser();
        }
    }

    /**
     * A signatory: signature image and name, kept together so they cannot be mismatched.
     *
     * @param image stored image, or {@code null} to print the name alone for handwritten signature
     */
    public record Signataire(byte[] image, String nom) {

        @Override
        public boolean equals(Object autre) {
            return autre instanceof Signataire(byte[] autreImage, String autreNom)
                    && java.util.Arrays.equals(image, autreImage) && java.util.Objects.equals(nom, autreNom);
        }

        @Override
        public int hashCode() {
            return 31 * java.util.Arrays.hashCode(image) + java.util.Objects.hashCode(nom);
        }

        @Override
        public String toString() {
            return "Signataire[image=" + (image == null ? "null" : image.length + " octets") + ", nom=" + nom + "]";
        }
    }

    private static byte[] image(Signataire signataire) {
        return signataire == null ? null : signataire.image();
    }

    private static String nom(Signataire signataire) {
        return signataire == null ? null : signataire.nom();
    }

    /** The validator's name: they commit the bank, not the requesting legal officer. */
    private static String nomComplet(Utilisateur utilisateur) {
        return utilisateur == null ? null : utilisateur.getPrenom() + " " + utilisateur.getNom();
    }

    /** The value, or the replacement when blank. */
    private static String ou(String valeur, String remplacement) {
        return valeur == null || valeur.isBlank() ? remplacement : valeur;
    }
}
