package com.afriland.audiences.common.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * La charte des documents produits par le système (Q-115 A2).
 *
 * <p><b>Ce qu'elle remplace.</b> Chaque générateur réinventait ses marges, sa police et son pas
 * vertical, et aucun ne portait la moindre marque : quatre documents anonymes, dont un partait chez
 * un prestataire. Rien n'était partagé — pas même l'idée qu'une page puisse être numérotée.
 *
 * <p><b>Ce qu'elle porte.</b> Le logo et la dénomination en tête, un filet rouge, un pied qui
 * numérote et attribue. Pas les mentions légales : celles-ci appartiennent au <b>papier à en-tête</b>
 * de la correspondance, et les porter sur un export de calendrier ferait passer un tableau de
 * travail pour une communication officielle de la banque.
 *
 * <p><b>Le tableau sait franchir les pages.</b> C'est l'essentiel de ce que la charte apporte : les
 * en-têtes sont redessinés à chaque page, et les cellules sont coupées à la <b>largeur mesurée</b> de
 * la police et non à un nombre de caractères. Helvetica étant proportionnelle, « IIIIIIII » et
 * « WWWWWWWW » n'occupent pas la même place ; compter les caractères revenait à déborder sur la
 * colonne voisine ou à tronquer trop tôt.
 *
 * <p><b>Le pied ne peut être écrit qu'à la fin</b> : « Page 2 sur 7 » suppose de connaître 7, ce
 * qu'on ignore tant que la dernière ligne n'est pas posée. Les pages sont donc reprises une seconde
 * fois, en ajout, au moment de {@link #finaliser()}.
 */
public final class DocumentPdf implements AutoCloseable {

    /** Rouge Afriland — réservé au filet d'en-tête, jamais au texte : il ne tient pas le AA. */
    private static final float[] ROUGE = {237 / 255f, 28 / 255f, 36 / 255f};
    private static final float[] ANTHRACITE = {35 / 255f, 31 / 255f, 32 / 255f};
    private static final float[] GRIS = {95 / 255f, 90 / 255f, 91 / 255f};
    private static final float[] TRAME = {237 / 255f, 238 / 255f, 241 / 255f};
    /**
     * Le fond de la colonne du papier à en-tête. Teinte distincte de {@link #TRAME} bien
     * qu'elles coïncident aujourd'hui : le bandeau d'une lettre et la trame d'un tableau n'ont
     * aucune raison d'évoluer ensemble, et ajuster l'un ne doit pas changer l'allure du
     * calendrier, du rapport et de l'archive d'audit.
     */
    private static final float[] FOND_COLONNE = {208 / 255f, 211 / 255f, 217 / 255f};

    private static final float MARGE = 40f;
    /*
     * La colonne latérale, **relevée au pixel sur le papier de la banque** (AFB, 1141 px de large) :
     * son aplat s'arrête à x=157, soit 13,76 % de la largeur — 82 pt sur A4. Les mentions y
     * commencent à 5,5 pt du bord, et la plus large (« N° COSUMAF-LMFAC-01/2015 ») occupe 68,9 pt,
     * ce qui impose une police de 5 pt : à 6,5 pt elle en réclamerait 91,4 et déborderait.
     *
     * {@code COLONNE} est la largeur **totale** de l'aplat depuis le bord gauche de la feuille,
     * bord perdu compris — et non la largeur du texte à partir de {@link #MARGE}.
     */
    private static final float COLONNE = 82f;
    /** Marge interne gauche de la colonne : le texte y commence presque au bord. */
    private static final float MARGE_COLONNE = 5.5f;
    /** Corps de la police des mentions — contraint par la largeur de la colonne. */
    private static final float TAILLE_COLONNE = 5f;
    /** Souffle entre l'aplat et le texte : le corps commence à 102 pt sur le contrat. */
    private static final float ECART_COLONNE = 20f;
    private static final float HAUTEUR_LOGO = 26f;
    private static final float HAUTEUR_LIGNE = 14f;
    /** Interligne à l'intérieur d'une cellule repliée : le corps de 8 pt, plus une respiration. */
    private static final float INTERLIGNE_CELLULE = 10f;
    private static final float PIED = 34f;

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Le logo, lu une fois pour toutes au chargement de la classe.
     *
     * <p>L'objet image PDFBox, lui, est lié à un document : il se crée par document, jamais une fois
     * pour toutes. Ce sont les octets qu'on garde.
     */
    private static final byte[] LOGO = chargerLogo();

    private final PDDocument document = new PDDocument();
    private final PDRectangle format;
    private final String titre;
    private final String sousTitre;
    private final String auteur;
    private final LibellesPdf libelles;
    private final List<PDPage> pages = new ArrayList<>();

    private final PDType1Font normale = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font grasse = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    /**
     * Vrai pour la correspondance : colonne latérale des mentions légales, logo centré, pas de
     * bandeau de titre. Faux pour les documents de travail — un export de calendrier portant
     * l'agrément ministériel se ferait passer pour une communication officielle de la banque.
     */
    private final boolean papierEnTete;

    /**
     * Faux pour le tirage destiné au <b>papier préimprimé</b> de la banque : le gabarit garde
     * exactement sa place — mêmes marges, même hauteur réservée en tête — mais n'est pas dessiné.
     * Le contenu tombe donc pile dans les zones laissées libres par la feuille imprimée d'avance.
     * <p>
     * Retirer le gabarit <b>sans</b> conserver sa géométrie aurait fait remonter le texte dans le
     * logo et courir les paragraphes sous la colonne : la page seule aurait paru correcte, et
     * l'impression aurait été illisible.
     */
    private final boolean dessinerGabarit;

    private PDPageContentStream flux;
    private PDImageXObject logo;
    private float y;

    /**
     * @param titre     ce que le document est ; il figure en tête, dans le pied et dans les
     *                  propriétés du fichier
     * @param sousTitre la période couverte, ou ce qui précise la portée ; peut être {@code null}
     * @param auteur    qui a demandé le document ; {@code null} quand l'appelant l'ignore
     * @param langue    langue des libellés ; {@code null} vaut français
     */
    public DocumentPdf(PDRectangle format, String titre, String sousTitre, String auteur, Locale langue) {
        this(format, titre, sousTitre, auteur, langue, false, true);
    }

    private DocumentPdf(PDRectangle format, String titre, String sousTitre, String auteur, Locale langue,
                        boolean papierEnTete, boolean dessinerGabarit) {
        this.format = format;
        this.titre = titre;
        this.sousTitre = sousTitre;
        this.auteur = auteur;
        this.libelles = LibellesPdf.pour(langue);
        this.papierEnTete = papierEnTete;
        this.dessinerGabarit = dessinerGabarit;
    }

    /**
     * Le papier à en-tête de la banque, pour la correspondance (B1).
     *
     * <p>Le titre ne s'imprime pas sur la page — une lettre n'en porte pas — mais il nomme le
     * fichier et son pied : il reste donc utile.
     */
    public static DocumentPdf papierEnTete(String titre, String auteur, Locale langue) {
        return new DocumentPdf(PDRectangle.A4, titre, null, auteur, langue, true, true);
    }

    /**
     * Le même document, <b>pour le papier préimprimé</b> de la banque : logo, aplat de la colonne et
     * mentions légales ne sont pas dessinés, puisque la feuille les porte déjà. Tout le reste est
     * identique — c'est la condition pour que le texte se pose au bon endroit une fois imprimé.
     */
    public static DocumentPdf gabaritPreimprime(String titre, String auteur, Locale langue) {
        return new DocumentPdf(PDRectangle.A4, titre, null, auteur, langue, true, false);
    }

    /** L'abscisse où le contenu commence : après la colonne latérale, s'il y en a une. */
    private float margeGauche() {
        return papierEnTete ? COLONNE + ECART_COLONNE : MARGE;
    }

    /** La largeur utile pour du texte, entre la marge gauche et la marge droite. */
    public float largeurUtile() {
        return format.getWidth() - margeGauche() - MARGE;
    }

    /** A4 à l'italienne — pour les tableaux larges, que le portrait comprime. */
    public static PDRectangle paysage() {
        return new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    }

    /** Ouvre une page neuve, dessine l'en-tête, et rend l'ordonnée où le contenu peut commencer. */
    public float nouvellePage() {
        try {
            if (flux != null) {
                flux.close();
            }
            PDPage page = new PDPage(format);
            document.addPage(page);
            pages.add(page);
            flux = new PDPageContentStream(document, page);
            y = format.getHeight() - MARGE;
            dessinerEnTete();
            return y;
        } catch (IOException e) {
            throw new UncheckedIOException("Échec de l'ouverture d'une page", e);
        }
    }

    /** Écrit une ligne de texte et descend d'un pas. */
    public void ligne(String texte, boolean gras, float taille) {
        ecrire(texte, margeGauche(), y, gras ? grasse : normale, taille, ANTHRACITE);
        y -= HAUTEUR_LIGNE;
    }

    /**
     * Écrit un paragraphe en le repliant sur la largeur utile (B1).
     *
     * <p>C'est ce qu'une lettre exige et qu'un tableau n'a jamais demandé : un texte long doit
     * revenir à la ligne de lui-même. Le repli se fait sur la <b>largeur mesurée</b>, mot à mot ; les
     * retours à la ligne déjà présents dans le texte sont respectés, ce qui permet à un rédacteur de
     * décider lui-même où couper.
     *
     * <p>Un mot plus long que la ligne entière — une référence, une adresse électronique — est écrit
     * tel quel plutôt que tronqué : dans une lettre, mieux vaut un débordement visible qu'une
     * information amputée en silence.
     */
    public void paragraphe(String texte, float taille) {
        if (texte == null) {
            return;
        }
        /*
         * On découpe **avant** de replier, et non l'inverse. `TextePdf.replier` transforme les
         * caractères de contrôle en espaces — sauts de ligne compris, puisque PDFBox les refuse : le
         * replier d'abord écraserait les paragraphes du texte en un seul bloc. Défaut trouvé par le
         * test, qui rendait le gabarit en un pavé continu là où il compte trois paragraphes.
         *
         * `lines()` traite aussi bien les fins de ligne Windows qu'Unix, et conserve les lignes
         * vides qui séparent les paragraphes.
         */
        for (String ligneBrute : texte.lines().toList()) {
            String ligneSource = TextePdf.replier(ligneBrute);
            if (ligneSource.isBlank()) {
                y -= taille * 0.9f;
                continue;
            }
            StringBuilder courante = new StringBuilder();
            for (String mot : ligneSource.split(" ")) {
                String essai = courante.isEmpty() ? mot : courante + " " + mot;
                if (!courante.isEmpty() && largeurDe(essai, normale, taille) > largeurUtile()) {
                    ecrireEtDescendre(courante.toString(), taille);
                    courante = new StringBuilder(mot);
                } else {
                    courante = new StringBuilder(essai);
                }
            }
            if (!courante.isEmpty()) {
                ecrireEtDescendre(courante.toString(), taille);
            }
        }
    }

    private void ecrireEtDescendre(String texte, float taille) {
        if (y < MARGE + PIED) {
            nouvellePage();
        }
        ecrire(texte, margeGauche(), y, normale, taille, ANTHRACITE);
        y -= taille * 1.45f;
    }

    /** Écrit un texte centré sur la largeur utile — le bloc de signature, notamment. */
    public void centre(String texte, boolean gras, float taille) {
        PDType1Font police = gras ? grasse : normale;
        float x = margeGauche() + (largeurUtile() - largeurDe(texte, police, taille)) / 2;
        ecrire(texte, x, y, police, taille, ANTHRACITE);
        y -= taille * 1.6f;
    }

    /**
     * Écrit deux textes sur **une même ligne** : l'un au fil de la marge, l'autre aligné à droite.
     *
     * C'est la première ligne du papier de la banque — le numéro à gauche, le lieu et la date à
     * droite, à la même hauteur. Les écrire l'un après l'autre les décalait d'un interligne.
     */
    public void ligneGaucheEtDroite(String gauche, String droite, float taille) {
        ecrire(gauche, margeGauche(), y, normale, taille, ANTHRACITE);
        ecrire(droite, format.getWidth() - MARGE - largeurDe(droite, normale, taille), y, normale,
                taille, ANTHRACITE);
        y -= taille * 1.6f;
    }

    /** Écrit un texte aligné à droite de la largeur utile — « Yaoundé, le … ». */
    public void ligneADroite(String texte, float taille) {
        float x = format.getWidth() - MARGE - largeurDe(texte, normale, taille);
        ecrire(texte, x, y, normale, taille, ANTHRACITE);
        y -= taille * 1.6f;
    }

    /** Écrit un texte à une abscisse choisie, sans descendre — le bloc destinataire, en retrait. */
    public void ligneEnRetrait(String texte, float retrait, boolean gras, float taille) {
        ecrire(texte, margeGauche() + retrait, y, gras ? grasse : normale, taille, ANTHRACITE);
        y -= taille * 1.35f;
    }

    /**
     * Écrit une étiquette soulignée suivie de sa valeur : « <u>Objet:</u> Lettre de constitution… ».
     * Le soulignement est tracé, non simulé par des tirets — c'est ainsi que le gabarit le porte.
     */
    public void etiquetteEtValeur(String etiquette, String valeur, float taille) {
        try {
            float x = margeGauche();
            ecrire(etiquette, x, y, grasse, taille, ANTHRACITE);
            float largeur = largeurDe(etiquette, grasse, taille);
            flux.setStrokingColor(ANTHRACITE[0], ANTHRACITE[1], ANTHRACITE[2]);
            flux.setLineWidth(0.6f);
            flux.moveTo(x, y - 1.5f);
            flux.lineTo(x + largeur, y - 1.5f);
            flux.stroke();
            ecrire(" " + valeur, x + largeur, y, normale, taille, ANTHRACITE);
            y -= taille * 1.6f;
        } catch (IOException e) {
            throw new UncheckedIOException("Échec du soulignement", e);
        }
    }

    /** Descend sans rien écrire — une respiration entre deux blocs. */
    public void espace(float hauteur) {
        y -= hauteur;
    }

    /**
     * Dessine un tableau, en ouvrant autant de pages qu'il en faut.
     *
     * @param entetes  libellés de colonnes, redessinés en tête de chaque page
     * @param largeurs largeur de chaque colonne, en points ; même longueur que {@code entetes}
     * @param lignes   les données, déjà mises en texte par l'appelant
     */
    public void tableau(List<String> entetes, float[] largeurs, List<List<String>> lignes) {
        tableau(entetes, largeurs, lignes, false);
    }

    /**
     * Variante où une cellule trop longue <b>passe à la ligne</b>, la rangée s'agrandissant d'autant,
     * au lieu d'être coupée par « ... ».
     *
     * <p>Réservée aux documents où rien ne doit se perdre : l'archive d'audit, pièce remise à un
     * contrôleur, dont la colonne « Détails » était tronquée. Le calendrier et le rapport gardent une
     * rangée par ligne : ce sont des vues de travail, et leur allure ne change pas.
     *
     * <p>Une rangée qui tient sur une ligne est dessinée <b>exactement</b> comme sans repli. Une rangée
     * repliée ne se coupe pas entre deux pages : le saut se décide sur sa hauteur réelle.
     */
    public void tableau(List<String> entetes, float[] largeurs, List<List<String>> lignes, boolean replier) {
        if (entetes.size() != largeurs.length) {
            throw new IllegalArgumentException("Autant de largeurs que de colonnes");
        }
        dessinerEnTeteTableau(entetes, largeurs);
        for (List<String> ligne : lignes) {
            List<List<String>> cellules = new ArrayList<>();
            int hauteur = 1;
            for (int i = 0; i < largeurs.length; i++) {
                String cellule = i < ligne.size() ? ligne.get(i) : "";
                List<String> morceaux = replier
                        ? replierCellule(cellule, largeurs[i] - 6)
                        : List.of(couper(cellule, largeurs[i] - 6, normale, 8f));
                cellules.add(morceaux);
                hauteur = Math.max(hauteur, morceaux.size());
            }
            float besoin = HAUTEUR_LIGNE + (hauteur - 1) * INTERLIGNE_CELLULE;
            // Pour une rangée d'une ligne, c'est la condition d'origine : y < MARGE + PIED + HAUTEUR_LIGNE.
            if (y - besoin < MARGE + PIED) {
                nouvellePage();
                dessinerEnTeteTableau(entetes, largeurs);
            }
            float x = margeGauche();
            for (int i = 0; i < largeurs.length; i++) {
                List<String> morceaux = cellules.get(i);
                for (int k = 0; k < morceaux.size(); k++) {
                    ecrire(morceaux.get(k), x + 3, y - k * INTERLIGNE_CELLULE, normale, 8f, ANTHRACITE);
                }
                x += largeurs[i];
            }
            y -= besoin;
        }
    }

    /**
     * Découpe une cellule en lignes qui tiennent dans {@code largeurMax}, mot à mot. Un mot plus large
     * que la colonne entière — une empreinte, une adresse — est coupé au caractère : il passe sur
     * plusieurs lignes, mais n'est jamais perdu.
     */
    private List<String> replierCellule(String texte, float largeurMax) {
        String replie = TextePdf.replier(texte);
        if (largeurDe(replie, normale, 8f) <= largeurMax) {
            return List.of(replie);
        }
        List<String> lignes = new ArrayList<>();
        StringBuilder courante = new StringBuilder();
        for (String mot : replie.split(" ")) {
            String essai = courante.isEmpty() ? mot : courante + " " + mot;
            if (largeurDe(essai, normale, 8f) <= largeurMax) {
                courante = new StringBuilder(essai);
                continue;
            }
            if (!courante.isEmpty()) {
                lignes.add(courante.toString());
                courante = new StringBuilder();
            }
            for (char caractere : mot.toCharArray()) {
                if (!courante.isEmpty() && largeurDe(courante.toString() + caractere, normale, 8f) > largeurMax) {
                    lignes.add(courante.toString());
                    courante = new StringBuilder();
                }
                courante.append(caractere);
            }
        }
        if (!courante.isEmpty()) {
            lignes.add(courante.toString());
        }
        return lignes;
    }

    /** Referme les flux, écrit les pieds de page puis les propriétés, et rend les octets. */
    public byte[] finaliser() {
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            if (flux != null) {
                flux.close();
                flux = null;
            }
            if (pages.isEmpty()) {
                nouvellePage();
                flux.close();
                flux = null;
            }
            dessinerPieds();
            /*
             * Le titre du **fichier** reprend la portée, là où celui de la **page** s'en dispense :
             * sur la page, la période est juste en dessous ; dans un explorateur ou une messagerie,
             * « Rapport MENSUEL » ne distinguerait pas deux mois.
             */
            String titreFichier = sousTitre == null || sousTitre.isBlank() ? titre : titre + " - " + sousTitre;
            MetadonneesPdf.appliquer(document, titreFichier, auteur);
            document.save(sortie);
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Échec de l'écriture du document", e);
        }
    }

    @Override
    public void close() {
        try {
            if (flux != null) {
                flux.close();
            }
            document.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Échec de la fermeture du document", e);
        }
    }

    // ───────────────────────────────────────────────────────────────────────────────────────────

    private void dessinerEnTete() throws IOException {
        if (papierEnTete) {
            dessinerPapierEnTete();
            return;
        }
        float hautDuBloc = y;
        if (LOGO.length > 0) {
            if (logo == null) {
                logo = PDImageXObject.createFromByteArray(document, LOGO, "logo");
            }
            float largeur = HAUTEUR_LOGO * logo.getWidth() / logo.getHeight();
            flux.drawImage(logo, MARGE, hautDuBloc - HAUTEUR_LOGO, largeur, HAUTEUR_LOGO);
        }
        // Dénomination à droite : la marque tient la page même là où le logo ne se voit pas.
        String organisation = libelles.texte("organisation");
        String direction = libelles.texte("direction");
        ecrireADroite(organisation, hautDuBloc - 10, grasse, 9f, ANTHRACITE);
        ecrireADroite(direction, hautDuBloc - 21, normale, 8f, GRIS);

        y = hautDuBloc - HAUTEUR_LOGO - 18;
        ecrire(titre, MARGE, y, grasse, 13f, ANTHRACITE);
        y -= 13;
        if (sousTitre != null && !sousTitre.isBlank()) {
            ecrire(sousTitre, MARGE, y, normale, 9f, GRIS);
            y -= 11;
        }
        // Le filet rouge sépare l'identité du contenu : c'est la seule couleur de la page.
        y -= 4;
        flux.setStrokingColor(ROUGE[0], ROUGE[1], ROUGE[2]);
        flux.setLineWidth(1f);
        flux.moveTo(MARGE, y);
        flux.lineTo(format.getWidth() - MARGE, y);
        flux.stroke();
        y -= 16;
    }

    /**
     * Le papier à en-tête : logo centré, puis la colonne des mentions légales à gauche.
     *
     * <p>C'est la disposition réelle de la banque, relevée sur sa correspondance — et non une
     * invention. La colonne se lit de haut en bas comme un bandeau, posé sur un <b>aplat de fond
     * gris</b> qui court du bord gauche de la feuille jusqu'au bord bas, bien au-delà de la dernière
     * mention : sur le papier de la banque, c'est le fond qui borne la colonne, pas un filet.
     */
    private void dessinerPapierEnTete() throws IOException {
        float haut = format.getHeight() - MARGE;
        if (LOGO.length > 0) {
            float hauteur = 30f;
            if (dessinerGabarit) {
                if (logo == null) {
                    logo = PDImageXObject.createFromByteArray(document, LOGO, "logo");
                }
                float largeur = hauteur * logo.getWidth() / logo.getHeight();
                flux.drawImage(logo, (format.getWidth() - largeur) / 2, haut - hauteur, largeur, hauteur);
            }
            // La hauteur est retranchée dans les deux cas : c'est elle qui réserve l'en-tête.
            haut -= hauteur + 24;
        }

        /*
         * L'aplat de la colonne. Son bord haut tombe **juste sous la ligne du numéro** — le haut de
         * la feuille reste blanc sur toute sa largeur — et il descend jusqu'au bord bas, en fond
         * perdu à gauche, bien au-delà de la dernière mention. C'est ce que fait le papier de la
         * banque, relevé au pixel. Dessiné avant les mentions, sans quoi il les
         * recouvrirait.
         */
        float hautAplat = haut - 20f;
        if (!dessinerGabarit) {
            // La colonne reste réservée par `margeGauche()` : rien à dessiner, rien à décaler.
            y = haut;
            return;
        }
        flux.setNonStrokingColor(FOND_COLONNE[0], FOND_COLONNE[1], FOND_COLONNE[2]);
        flux.addRect(0, 0, COLONNE, hautAplat);
        flux.fill();

        float yColonne = hautAplat - 10f;
        for (IdentiteBancaire.Mention mention : IdentiteBancaire.COLONNE) {
            if (mention.barre()) {
                // Les trois barres du gabarit : elles courent sur la largeur de la colonne, moins
                // la respiration de droite, et se posent a mi-interligne.
                flux.setStrokingColor(ANTHRACITE[0], ANTHRACITE[1], ANTHRACITE[2]);
                flux.setLineWidth(0.6f);
                flux.moveTo(MARGE_COLONNE, yColonne + 2f);
                flux.lineTo(COLONNE - 10f, yColonne + 2f);
                flux.stroke();
            } else if (!mention.texte().isEmpty()) {
                ecrire(mention.texte(), MARGE_COLONNE, yColonne,
                        mention.gras() ? grasse : normale, TAILLE_COLONNE, ANTHRACITE);
            }
            yColonne -= 8.5f;
        }

        y = haut;
    }

    private void dessinerEnTeteTableau(List<String> entetes, float[] largeurs) {
        try {
            float largeurTotale = 0;
            for (float largeur : largeurs) {
                largeurTotale += largeur;
            }
            flux.setNonStrokingColor(TRAME[0], TRAME[1], TRAME[2]);
            flux.addRect(margeGauche(), y - 4, largeurTotale, HAUTEUR_LIGNE);
            flux.fill();

            float x = margeGauche();
            for (int i = 0; i < largeurs.length; i++) {
                ecrire(couper(entetes.get(i), largeurs[i] - 6, grasse, 8f), x + 3, y, grasse, 8f, ANTHRACITE);
                x += largeurs[i];
            }
            y -= HAUTEUR_LIGNE + 2;
        } catch (IOException e) {
            throw new UncheckedIOException("Échec du dessin de l'en-tête de tableau", e);
        }
    }

    private void dessinerPieds() throws IOException {
        /*
         * **Le papier à en-tête n'en porte pas.** Le gabarit de la banque s'arrête au bloc de
         * signature : ni numérotation, ni mention d'édition. Un pied y traverserait d'ailleurs
         * l'aplat de la colonne, que rien ne borne à gauche.
         *
         * Ce que cela coûte, et qui est assumé : une lettre de plusieurs feuillets ne dira plus
         * « Page 1 sur 2 », si bien qu'un destinataire ne verra pas qu'il en manque une. C'est le
         * choix du papier réel, pas un oubli.
         *
         * Les documents de travail — calendrier, rapport, archive d'audit — gardent le leur : ils
         * se lisent à l'écran ou s'impriment en liasse, et la pagination y est la seule garantie
         * qu'on tient le document entier (A2).
         */
        if (papierEnTete) {
            return;
        }

        String editePar = auteur == null || auteur.isBlank()
                ? libelles.texte("pied.editeLe", LocalDate.now().format(JOUR))
                : libelles.texte("pied.editePar", auteur);

        for (int i = 0; i < pages.size(); i++) {
            PDPage page = pages.get(i);
            try (PDPageContentStream pied = new PDPageContentStream(document, page, AppendMode.APPEND, true)) {
                float base = MARGE - 6;
                pied.setStrokingColor(GRIS[0], GRIS[1], GRIS[2]);
                pied.setLineWidth(0.4f);
                pied.moveTo(MARGE, base + 12);
                pied.lineTo(format.getWidth() - MARGE, base + 12);
                pied.stroke();

                ecrireSur(pied, titre, MARGE, base, normale, 7.5f, GRIS);
                String numero = libelles.texte("pied.page", i + 1, pages.size());
                float largeurNumero = largeurDe(numero, normale, 7.5f);
                ecrireSur(pied, numero, (format.getWidth() - largeurNumero) / 2, base, normale, 7.5f, GRIS);
                float largeurAuteur = largeurDe(editePar, normale, 7.5f);
                ecrireSur(pied, editePar, format.getWidth() - MARGE - largeurAuteur, base, normale, 7.5f, GRIS);
            }
        }
    }

    private void ecrire(String texte, float x, float ordonnee, PDType1Font police, float taille, float[] couleur) {
        try {
            ecrireSur(flux, texte, x, ordonnee, police, taille, couleur);
        } catch (IOException e) {
            throw new UncheckedIOException("Échec de l'écriture d'un texte", e);
        }
    }

    private void ecrireADroite(String texte, float ordonnee, PDType1Font police, float taille, float[] couleur) {
        ecrire(texte, format.getWidth() - MARGE - largeurDe(texte, police, taille), ordonnee, police, taille, couleur);
    }

    private static void ecrireSur(PDPageContentStream cible, String texte, float x, float ordonnee,
                                  PDType1Font police, float taille, float[] couleur) throws IOException {
        cible.setNonStrokingColor(couleur[0], couleur[1], couleur[2]);
        cible.setFont(police, taille);
        cible.beginText();
        cible.newLineAtOffset(x, ordonnee);
        cible.showText(TextePdf.replier(texte));
        cible.endText();
    }

    /** Largeur réelle d'un texte, telle que la police la produira. */
    private static float largeurDe(String texte, PDType1Font police, float taille) {
        try {
            return police.getStringWidth(TextePdf.replier(texte)) / 1000 * taille;
        } catch (IOException e) {
            throw new UncheckedIOException("Échec du calcul de largeur", e);
        }
    }

    /**
     * Coupe un texte pour qu'il tienne dans une largeur donnée, en le terminant par des points de
     * suspension. On mesure au lieu de compter : c'est la seule façon de ne pas empiéter sur la
     * colonne voisine.
     */
    private static String couper(String texte, float largeurMax, PDType1Font police, float taille) {
        String replie = TextePdf.replier(texte);
        if (largeurDe(replie, police, taille) <= largeurMax) {
            return replie;
        }
        String suite = "...";
        float largeurSuite = largeurDe(suite, police, taille);
        StringBuilder coupe = new StringBuilder();
        for (char caractere : replie.toCharArray()) {
            if (largeurDe(coupe.toString() + caractere, police, taille) + largeurSuite > largeurMax) {
                break;
            }
            coupe.append(caractere);
        }
        return coupe + suite;
    }

    /**
     * Le logo, ou rien.
     *
     * <p>Un logo introuvable ne doit pas empêcher un document de sortir : un calendrier sans image
     * reste utilisable, un calendrier qui n'existe pas ne l'est pas. L'absence se voit assez pour
     * être signalée sans avoir à lever.
     */
    private static byte[] chargerLogo() {
        try (InputStream flux = DocumentPdf.class.getResourceAsStream("/marque/logo-afriland.png")) {
            return flux == null ? new byte[0] : flux.readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
