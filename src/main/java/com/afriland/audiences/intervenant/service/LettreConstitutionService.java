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
 * Compose et met en forme la lettre de constitution (RG-INT-08, FR-INT-07/US 5.7 — Q-115 B1).
 *
 * <p><b>Ce qui change.</b> La lettre était cinq lignes posées sur une page nue : un titre, le
 * dossier, le prestataire, le motif, le validateur. Elle prend le <b>papier à en-tête</b> de la
 * banque — logo, colonne des mentions légales — et la forme que la direction juridique emploie
 * réellement : un numéro, un destinataire, un objet, un corps, un signataire.
 *
 * <p><b>Les pointillés sont conservés.</b> Le gabarit officiel est fait pour être complété à la main ;
 * un champ laissé vide rend donc des pointillés, et non le vide. Les supprimer reviendrait à retirer
 * à la direction juridique une possibilité qu'elle emploie aujourd'hui.
 *
 * <p><b>Le motif n'y figure plus.</b> C'est une justification interne, adressée au supérieur
 * hiérarchique et absente du gabarit officiel : un huissier n'a pas à lire pourquoi la banque a
 * décidé en interne de le constituer. La traçabilité du validateur demeure — dans les propriétés du
 * fichier et dans la GED.
 */
@Service
public class LettreConstitutionService {

    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Ce que rend un champ vide : le gabarit papier porte ces pointillés pour être complété. */
    private static final String POINTILLES = "..........................";

    /** Valeurs par défaut, relevées sur le gabarit de la direction juridique. */
    public static final String SUFFIXE_PAR_DEFAUT = "First Bank/DAJR/DR/CM/ABN/18";
    public static final String LIEU_PAR_DEFAUT = "Yaoundé";
    public static final String OBJET_PAR_DEFAUT = "Lettre de constitution contenant transmission de pièces.";
    public static final String SIGNATAIRE_PAR_DEFAUT = "La Direction Juridique";
    public static final String QUALITE_PAR_DEFAUT = "Huissier de Justice";

    /**
     * Le corps type, tel qu'il figure sur le gabarit.
     *
     * <p>Les pointillés y sont laissés : ce sont les endroits que le rédacteur complète — le type de
     * saisie, le débiteur, le montant en chiffres puis en lettres, le tiers saisi, les annexes.
     * <b>On ne pré-remplit que ce dont on est certain</b>, et aucune de ces valeurs ne se déduit du
     * dossier sans risque : le « client » d'un dossier de recouvrement n'est pas nécessairement le
     * débiteur visé par la saisie, et le risque encouru n'est pas le montant de la créance.
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
     * Le brouillon d'une demande : tout ce qui est certain, le reste en pointillés.
     *
     * @param numero {@code null} tant que la lettre n'est pas signée — le réserver dès la rédaction
     *               brûlerait un numéro à chaque demande sans suite, et les trous s'accumuleraient
     *               définitivement dans une série qui ne repart jamais à un (B2)
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

    /** Met la lettre en page sur le papier à en-tête de la banque. */
    public byte[] generer(LettreConstitution lettre) {
        String titre = "Lettre de constitution " + lettre.referenceComplete();
        Utilisateur validateur = lettre.getDemande() == null ? null : lettre.getDemande().getValidePar();
        try (DocumentPdf document = DocumentPdf.papierEnTete(titre, nomComplet(validateur), Locale.FRENCH)) {
            document.nouvellePage();

            // La ligne du numéro à gauche, le lieu et la date à droite — sur la MÊME ligne, comme sur
            // le gabarit. Deux écritures successives les décalaient d'un interligne.
            document.ligneGaucheEtDroite(lettre.referenceComplete(),
                    ou(lettre.getLieu(), LIEU_PAR_DEFAUT) + " le " + LocalDate.now().format(FORMAT_DATE),
                    10f);
            document.espace(18f);

            // Le destinataire, en retrait : c'est la disposition d'une lettre, pas d'un formulaire.
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

            document.centre(ou(lettre.getSignataire(), SIGNATAIRE_PAR_DEFAUT), true, 10f);
            return document.finaliser();
        }
    }

    /** Le validateur : c'est lui qui engage la banque, pas le juriste qui a sollicité. */
    private static String nomComplet(Utilisateur utilisateur) {
        return utilisateur == null ? null : utilisateur.getPrenom() + " " + utilisateur.getNom();
    }

    /** La valeur, ou son remplacement quand elle manque — jamais le vide. */
    private static String ou(String valeur, String remplacement) {
        return valeur == null || valeur.isBlank() ? remplacement : valeur;
    }
}
