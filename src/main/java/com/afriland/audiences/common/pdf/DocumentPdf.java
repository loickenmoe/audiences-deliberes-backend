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

    private static final float MARGE = 40f;
    private static final float HAUTEUR_LOGO = 26f;
    private static final float HAUTEUR_LIGNE = 14f;
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
        this.format = format;
        this.titre = titre;
        this.sousTitre = sousTitre;
        this.auteur = auteur;
        this.libelles = LibellesPdf.pour(langue);
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
        ecrire(texte, MARGE, y, gras ? grasse : normale, taille, ANTHRACITE);
        y -= HAUTEUR_LIGNE;
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
        if (entetes.size() != largeurs.length) {
            throw new IllegalArgumentException("Autant de largeurs que de colonnes");
        }
        dessinerEnTeteTableau(entetes, largeurs);
        for (List<String> ligne : lignes) {
            if (y < MARGE + PIED + HAUTEUR_LIGNE) {
                nouvellePage();
                dessinerEnTeteTableau(entetes, largeurs);
            }
            float x = MARGE;
            for (int i = 0; i < largeurs.length; i++) {
                String cellule = i < ligne.size() ? ligne.get(i) : "";
                ecrire(couper(cellule, largeurs[i] - 6, normale, 8f), x + 3, y, normale, 8f, ANTHRACITE);
                x += largeurs[i];
            }
            y -= HAUTEUR_LIGNE;
        }
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

    private void dessinerEnTeteTableau(List<String> entetes, float[] largeurs) {
        try {
            float largeurTotale = 0;
            for (float largeur : largeurs) {
                largeurTotale += largeur;
            }
            flux.setNonStrokingColor(TRAME[0], TRAME[1], TRAME[2]);
            flux.addRect(MARGE, y - 4, largeurTotale, HAUTEUR_LIGNE);
            flux.fill();

            float x = MARGE;
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
