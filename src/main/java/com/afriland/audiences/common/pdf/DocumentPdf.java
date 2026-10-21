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
 * Shared layout for documents produced by the system: logo and organisation name in the header, a
 * red rule, and a footer with page numbering and attribution. Legal mentions belong only to the
 * correspondence letterhead.
 *
 * <p>Tables span pages: headers are redrawn on each page and cells are cut at the measured font
 * width, not a character count (Helvetica is proportional).
 *
 * <p>Footers are written in {@link #finaliser()}, since "Page 2 of 7" requires the total page count.
 */
public final class DocumentPdf implements AutoCloseable {

    /** Afriland red, for the header rule only, never for text: it does not meet AA contrast. */
    private static final float[] ROUGE = {237 / 255f, 28 / 255f, 36 / 255f};
    private static final float[] ANTHRACITE = {35 / 255f, 31 / 255f, 32 / 255f};
    private static final float[] GRIS = {95 / 255f, 90 / 255f, 91 / 255f};
    private static final float[] TRAME = {237 / 255f, 238 / 255f, 241 / 255f};
    /**
     * Letterhead column background. Kept distinct from {@link #TRAME} although identical today, so
     * adjusting the letter does not change the look of the working documents.
     */
    private static final float[] FOND_COLONNE = {208 / 255f, 211 / 255f, 217 / 255f};

    private static final float MARGE = 40f;
    /*
     * Side column measured on the bank's letterhead: the fill ends at 13.76% of the page width (82 pt
     * on A4). Mentions start 5.5 pt from the edge; the widest one ("N° COSUMAF-LMFAC-01/2015") needs
     * a 5 pt font to fit.
     *
     * {@code COLONNE} is the total fill width from the sheet's left edge (bleed included), not the
     * text width from {@link #MARGE}.
     */
    private static final float COLONNE = 82f;
    /** Left padding of the column: text starts almost at the edge. */
    private static final float MARGE_COLONNE = 5.5f;
    /** Font size of the mentions, constrained by the column width. */
    private static final float TAILLE_COLONNE = 5f;
    /** Gap between the column fill and the body text (body starts at 102 pt). */
    private static final float ECART_COLONNE = 20f;
    /** Gutter between the two signature columns. */
    private static final float ECART_SIGNATURES = 24f;
    /** Size of the name under a signature, matching the letter body. */
    private static final float TAILLE_SIGNATAIRE = 10f;
    private static final float HAUTEUR_LOGO = 26f;
    private static final float HAUTEUR_LIGNE = 14f;
    /** Line spacing inside a wrapped cell: 8 pt font plus some breathing room. */
    private static final float INTERLIGNE_CELLULE = 10f;
    private static final float PIED = 34f;

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Logo bytes, loaded once. The PDFBox image object is bound to a document, so it is created per
     * document.
     */
    private static final byte[] LOGO_OCTETS = chargerLogo();

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
     * True for correspondence: legal mentions side column, centered logo, no title banner. False for
     * working documents, which must not look like an official bank communication.
     */
    private final boolean papierEnTete;

    /**
     * False when printing on the bank's pre-printed paper: the template keeps its exact geometry
     * (margins, header height) but is not drawn, so content lands in the free areas of the sheet.
     */
    private final boolean dessinerGabarit;

    private PDPageContentStream flux;
    private PDImageXObject logo;
    private float y;

    /**
     * @param titre     what the document is; shown in the header, footer and file properties
     * @param sousTitre period covered or scope details; may be {@code null}
     * @param auteur    requester; {@code null} when unknown
     * @param langue    label language; {@code null} means French
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
     * The bank's letterhead, for correspondence. The title is not printed on the page but still names
     * the file.
     */
    public static DocumentPdf papierEnTete(String titre, String auteur, Locale langue) {
        return new DocumentPdf(PDRectangle.A4, titre, null, auteur, langue, true, true);
    }

    /**
     * Same document for the bank's pre-printed paper: logo, column fill and legal mentions are not
     * drawn, everything else is identical so text lands in the right place once printed.
     */
    public static DocumentPdf gabaritPreimprime(String titre, String auteur, Locale langue) {
        return new DocumentPdf(PDRectangle.A4, titre, null, auteur, langue, true, false);
    }

    /** X coordinate where content starts: after the side column, if any. */
    private float margeGauche() {
        return papierEnTete ? COLONNE + ECART_COLONNE : MARGE;
    }

    /** Usable text width between the left and right margins. */
    public float largeurUtile() {
        return format.getWidth() - margeGauche() - MARGE;
    }

    /** Landscape A4, for wide tables. */
    public static PDRectangle paysage() {
        return new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    }

    /** Opens a new page, draws the header and returns the Y coordinate where content can start. */
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

    /** Writes one line of text and moves down one step. */
    public void ligne(String texte, boolean gras, float taille) {
        ecrire(texte, margeGauche(), y, gras ? grasse : normale, taille, ANTHRACITE);
        y -= HAUTEUR_LIGNE;
    }

    /**
     * Writes a paragraph wrapped word by word on the measured usable width. Existing line breaks are
     * kept. A word longer than the whole line is written as is rather than truncated.
     */
    public void paragraphe(String texte, float taille) {
        if (texte == null) {
            return;
        }
        /*
         * Split into lines before folding: `TextePdf.replier` turns control characters (including
         * line breaks) into spaces, which would merge paragraphs. `lines()` handles both Windows and
         * Unix line endings and keeps blank separator lines.
         */
        for (String ligneBrute : texte.lines().toList()) {
            String ligneSource = TextePdf.replier(ligneBrute);
            if (ligneSource.isBlank()) {
                y -= taille * 0.9f;
            } else {
                ecrireLigneRepliee(ligneSource, taille);
            }
        }
    }

    /** Folds one source line word by word on the usable width. */
    private void ecrireLigneRepliee(String ligneSource, float taille) {
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

    private void ecrireEtDescendre(String texte, float taille) {
        if (y < MARGE + PIED) {
            nouvellePage();
        }
        ecrire(texte, margeGauche(), y, normale, taille, ANTHRACITE);
        y -= taille * 1.45f;
    }

    /**
     * The two signatories of a letter side by side: drafter on the left, approver on the right, below
     * the issuing department's name.
     *
     * <p>Both names stay aligned even if only one signature image exists (the image band has the same
     * height on both sides). Image aspect ratio is preserved; an image too wide is scaled down.
     *
     * @param hauteur height of the band reserved for each signature, in points
     */
    public void deuxSignatures(byte[] imageGauche, String nomGauche,
                               byte[] imageDroite, String nomDroite, float hauteur) {
        float largeurColonne = (largeurUtile() - ECART_SIGNATURES) / 2;
        float centreGauche = margeGauche() + largeurColonne / 2;
        float centreDroite = format.getWidth() - MARGE - largeurColonne / 2;
        float sommet = y;

        apposer(imageGauche, centreGauche, sommet, largeurColonne, hauteur);
        apposer(imageDroite, centreDroite, sommet, largeurColonne, hauteur);

        float ligneDesNoms = sommet - hauteur - 6f;
        centrerSur(nomGauche, centreGauche, ligneDesNoms);
        centrerSur(nomDroite, centreDroite, ligneDesNoms);
        y = ligneDesNoms - 16f;
    }

    /**
     * Places a signature image at the bottom of its band.
     *
     * <p>An unreadable image never fails the letter: it is skipped and the letter is signed by hand.
     * The catch is deliberately broad because PDFBox decoders can throw runtime exceptions (e.g.
     * {@code ArrayIndexOutOfBoundsException} on a truncated PNG), not just {@code IOException}.
     */
    private void apposer(byte[] image, float centre, float sommet, float largeurMax, float hauteur) {
        if (image == null || image.length == 0) {
            return;
        }
        try {
            PDImageXObject vignette = PDImageXObject.createFromByteArray(document, image, "signature");
            float largeur = hauteur * vignette.getWidth() / vignette.getHeight();
            float haute = hauteur;
            if (largeur > largeurMax) {
                largeur = largeurMax;
                haute = largeur * vignette.getHeight() / vignette.getWidth();
            }
            // Placed at the bottom of the band so the stroke touches the name, like a handwritten signature.
            flux.drawImage(vignette, centre - largeur / 2, sommet - hauteur, largeur, haute);
        } catch (java.io.IOException | RuntimeException erreur) {
            // Intentionally ignored: the letter takes precedence over the image (see Javadoc).
        }
    }

    /** Name centered on its column axis. If absent, nothing is written and nothing shifts. */
    private void centrerSur(String texte, float centre, float ordonnee) {
        if (texte == null || texte.isBlank()) {
            return;
        }
        ecrire(texte, centre - largeurDe(texte, grasse, TAILLE_SIGNATAIRE) / 2, ordonnee,
                grasse, TAILLE_SIGNATAIRE, ANTHRACITE);
    }

    /** Writes text centered on the usable width (e.g. the signature block). */

    public void centre(String texte, boolean gras, float taille) {
        PDType1Font police = gras ? grasse : normale;
        float x = margeGauche() + (largeurUtile() - largeurDe(texte, police, taille)) / 2;
        ecrire(texte, x, y, police, taille, ANTHRACITE);
        y -= taille * 1.6f;
    }

    /**
     * Writes two texts on the same line: one at the left margin, the other right-aligned (e.g. the
     * reference number and the place and date on the bank's letter).
     */
    public void ligneGaucheEtDroite(String gauche, String droite, float taille) {
        ecrire(gauche, margeGauche(), y, normale, taille, ANTHRACITE);
        ecrire(droite, format.getWidth() - MARGE - largeurDe(droite, normale, taille), y, normale,
                taille, ANTHRACITE);
        y -= taille * 1.6f;
    }

    /** Writes right-aligned text (e.g. the place and date line). */
    public void ligneADroite(String texte, float taille) {
        float x = format.getWidth() - MARGE - largeurDe(texte, normale, taille);
        ecrire(texte, x, y, normale, taille, ANTHRACITE);
        y -= taille * 1.6f;
    }

    /** Writes text at a given indent (e.g. the recipient block). */
    public void ligneEnRetrait(String texte, float retrait, boolean gras, float taille) {
        ecrire(texte, margeGauche() + retrait, y, gras ? grasse : normale, taille, ANTHRACITE);
        y -= taille * 1.35f;
    }

    /**
     * Writes an underlined label followed by its value. The underline is drawn, not simulated with
     * dashes, as on the template.
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

    /** Moves down without writing anything. */
    public void espace(float hauteur) {
        y -= hauteur;
    }

    /**
     * Draws a table, opening as many pages as needed.
     *
     * @param entetes  column labels, redrawn at the top of each page
     * @param largeurs width of each column in points; same length as {@code entetes}
     * @param lignes   data, already converted to text by the caller
     */
    public void tableau(List<String> entetes, float[] largeurs, List<List<String>> lignes) {
        tableau(entetes, largeurs, lignes, false);
    }

    /**
     * Variant where an overlong cell wraps (the row grows) instead of being cut with "...". Used where
     * nothing may be lost, such as the audit archive. A single-line row renders exactly as without
     * wrapping, and a wrapped row is never split across pages.
     */
    public void tableau(List<String> entetes, float[] largeurs, List<List<String>> lignes, boolean replier) {
        if (entetes.size() != largeurs.length) {
            throw new IllegalArgumentException("Autant de largeurs que de colonnes");
        }
        dessinerEnTeteTableau(entetes, largeurs);
        for (List<String> ligne : lignes) {
            List<List<String>> cellules = decouperLigne(ligne, largeurs, replier);
            int hauteur = cellules.stream().mapToInt(List::size).max().orElse(1);
            float besoin = HAUTEUR_LIGNE + (Math.max(hauteur, 1) - 1) * INTERLIGNE_CELLULE;
            // For a single-line row this is equivalent to y < MARGE + PIED + HAUTEUR_LIGNE.
            if (y - besoin < MARGE + PIED) {
                nouvellePage();
                dessinerEnTeteTableau(entetes, largeurs);
            }
            ecrireLigne(cellules, largeurs);
            y -= besoin;
        }
    }

    /** The lines of each cell of a row: folded when {@code replier}, otherwise cut to fit. */
    private List<List<String>> decouperLigne(List<String> ligne, float[] largeurs, boolean replier) {
        List<List<String>> cellules = new ArrayList<>();
        for (int i = 0; i < largeurs.length; i++) {
            String cellule = i < ligne.size() ? ligne.get(i) : "";
            cellules.add(replier
                    ? replierCellule(cellule, largeurs[i] - 6)
                    : List.of(couper(cellule, largeurs[i] - 6, normale, 8f)));
        }
        return cellules;
    }

    private void ecrireLigne(List<List<String>> cellules, float[] largeurs) {
        float x = margeGauche();
        for (int i = 0; i < largeurs.length; i++) {
            List<String> morceaux = cellules.get(i);
            for (int k = 0; k < morceaux.size(); k++) {
                ecrire(morceaux.get(k), x + 3, y - k * INTERLIGNE_CELLULE, normale, 8f, ANTHRACITE);
            }
            x += largeurs[i];
        }
    }

    /**
     * Splits a cell into lines fitting {@code largeurMax}, word by word. A word wider than the column
     * (a hash, an address) is cut by character across lines, never lost.
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

    /** Closes streams, writes footers and properties, and returns the bytes. */
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
             * The file title includes the scope, unlike the page title: in a file explorer, "Monthly
             * report" alone would not distinguish two months.
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

    private void dessinerEnTete() throws IOException {
        if (papierEnTete) {
            dessinerPapierEnTete();
            return;
        }
        float hautDuBloc = y;
        if (LOGO_OCTETS.length > 0) {
            if (logo == null) {
                logo = PDImageXObject.createFromByteArray(document, LOGO_OCTETS, "logo");
            }
            float largeur = HAUTEUR_LOGO * logo.getWidth() / logo.getHeight();
            flux.drawImage(logo, MARGE, hautDuBloc - HAUTEUR_LOGO, largeur, HAUTEUR_LOGO);
        }
        // Organisation name on the right, so branding remains even without the logo.
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
        // The red rule separates identity from content; it is the page's only color.
        y -= 4;
        flux.setStrokingColor(ROUGE[0], ROUGE[1], ROUGE[2]);
        flux.setLineWidth(1f);
        flux.moveTo(MARGE, y);
        flux.lineTo(format.getWidth() - MARGE, y);
        flux.stroke();
        y -= 16;
    }

    /**
     * Letterhead: centered logo, then the legal mentions column on the left, on a grey fill running
     * from the left edge down to the bottom of the sheet, as on the bank's actual correspondence.
     */
    private void dessinerPapierEnTete() throws IOException {
        float haut = format.getHeight() - MARGE;
        if (LOGO_OCTETS.length > 0) {
            float hauteur = 30f;
            if (dessinerGabarit) {
                if (logo == null) {
                    logo = PDImageXObject.createFromByteArray(document, LOGO_OCTETS, "logo");
                }
                float largeur = hauteur * logo.getWidth() / logo.getHeight();
                flux.drawImage(logo, (format.getWidth() - largeur) / 2, haut - hauteur, largeur, hauteur);
            }
            // Height is reserved in both cases: it keeps the header space.
            haut -= hauteur + 24;
        }

        /*
         * Column fill: its top edge sits just below the reference-number line and it bleeds down to
         * the bottom edge. Drawn before the mentions so it does not cover them.
         */
        float hautAplat = haut - 20f;
        if (!dessinerGabarit) {
            // The column stays reserved by `margeGauche()`: nothing to draw, nothing to shift.
            y = haut;
            return;
        }
        flux.setNonStrokingColor(FOND_COLONNE[0], FOND_COLONNE[1], FOND_COLONNE[2]);
        flux.addRect(0, 0, COLONNE, hautAplat);
        flux.fill();

        float yColonne = hautAplat - 10f;
        for (IdentiteBancaire.Mention mention : IdentiteBancaire.COLONNE) {
            if (mention.barre()) {
                // The template's three rules span the column width minus right padding, at
                // half line spacing.
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
         * The letterhead has no footer, matching the bank's template (it would also cross the column
         * fill). A multi-page letter therefore shows no "Page 1 of 2". Working documents keep their
         * footer, since pagination is the only guarantee the document is complete.
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

    /** Actual text width as rendered by the font. */
    private static float largeurDe(String texte, PDType1Font police, float taille) {
        try {
            return police.getStringWidth(TextePdf.replier(texte)) / 1000 * taille;
        } catch (IOException e) {
            throw new UncheckedIOException("Échec du calcul de largeur", e);
        }
    }

    /**
     * Cuts a text to fit a width, ending with an ellipsis. Measured rather than counted, so it never
     * spills into the next column.
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
     * The logo, or an empty array. A missing logo must not prevent a document from being produced.
     */
    private static byte[] chargerLogo() {
        try (InputStream flux = DocumentPdf.class.getResourceAsStream("/marque/logo-afriland.png")) {
            return flux == null ? new byte[0] : flux.readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
