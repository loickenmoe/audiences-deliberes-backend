package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.pdf.MetadonneesPdf;
import com.afriland.audiences.common.pdf.TextePdf;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Met en forme une extraction d'audit (Q-113), sur le modèle de
 * {@code CalendrierExportService} : une liste de lignes, et non un agrégat.
 *
 * <p><b>Le PDF est écrit en Helvetica, qui ne couvre pas tout l'Unicode.</b> Une seule apostrophe
 * typographique suffit à faire échouer PDFBox en pleine génération. Le repli vit désormais dans
 * {@link com.afriland.audiences.common.pdf.TextePdf}, partagé par les quatre documents du système :
 * la copie privée qui se trouvait ici ne protégeait qu'un générateur sur quatre (A1).
 */
@Service
public class AuditExportRenderer {

    private static final String[] EN_TETES = {"Date", "Auteur", "Dossier", "Action", "Détails"};
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Une ligne prête à écrire : le service a déjà résolu l'auteur et mis les détails à plat. */
    public record LigneExport(String horodatage, String auteur, String dossier, String action, String details) {
    }

    /**
     * @param titre  porte la référence de l'archive, seule chose l'identifiant que le fichier puisse
     *               contenir : tout ce qui décrit son contenu — l'empreinte d'abord — changerait les
     *               octets en y étant écrit, donc changerait ce qu'il décrit
     * @param auteur nom de qui a demandé l'extraction, porté par le pied de page
     */
    public byte[] pdf(List<LigneExport> lignes, LocalDate dateDebut, LocalDate dateFin, String titre,
                         String auteur) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Paysage : cinq colonnes dont deux longues ne tiennent pas en portrait.
            PDRectangle format = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            var police = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            var policeGrasse = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            PDPage page = null;
            PDPageContentStream cs = null;
            float y = 0;
            try {
                for (int i = 0; i < lignes.size(); i++) {
                    if (cs == null || y < 40) {
                        if (cs != null) cs.close();
                        page = new PDPage(format);
                        document.addPage(page);
                        cs = new PDPageContentStream(document, page);
                        y = format.getHeight() - 45;
                        ecrire(cs, policeGrasse, 12, 40, y, titre);
                        y -= 16;
                        ecrire(cs, police, 8, 40, y,
                                "Periode du " + dateDebut + " au " + dateFin + " - " + lignes.size() + " ligne(s)");
                        y -= 18;
                        ecrire(cs, policeGrasse, 8, 40, y, ligneTexte(
                                EN_TETES[0], EN_TETES[1], EN_TETES[2], EN_TETES[3], EN_TETES[4]));
                        y -= 12;
                    }
                    LigneExport l = lignes.get(i);
                    ecrire(cs, police, 8, 40, y,
                            ligneTexte(l.horodatage(), l.auteur(), l.dossier(), l.action(), l.details()));
                    y -= 11;
                }
                if (cs == null) {
                    // Aucune ligne : une page qui le dit vaut mieux qu'un PDF vide, illisible et
                    // qu'on prendrait pour un fichier corrompu.
                    page = new PDPage(format);
                    document.addPage(page);
                    cs = new PDPageContentStream(document, page);
                    ecrire(cs, policeGrasse, 12, 40, format.getHeight() - 45, titre);
                    ecrire(cs, police, 10, 40, format.getHeight() - 70,
                            "Aucune action sur la periode du " + dateDebut + " au " + dateFin + ".");
                }
                /*
                 * Pied de page auto-descriptif. Un document d'audit détaché de l'application doit
                 * dire lui-même ce qu'il est : sans quoi une page imprimée devient anonyme, et il
                 * faut recalculer son empreinte rien que pour savoir de quoi on parle.
                 */
                if (cs != null) {
                    cs.close();
                    cs = null;
                }
                PDPage colophon = new PDPage(format);
                document.addPage(colophon);
                cs = new PDPageContentStream(document, colophon);
                float yc = format.getHeight() - 60;
                ecrire(cs, policeGrasse, 11, 40, yc, titre);
                yc -= 22;
                for (String mention : List.of(
                        "Periode couverte  : du " + dateDebut + " au " + dateFin,
                        "Lignes            : " + lignes.size(),
                        "Extrait par       : " + (auteur == null || auteur.isBlank() ? "-" : auteur),
                        "Extrait le        : " + horodater(java.time.LocalDateTime.now()),
                        "",
                        "L'empreinte SHA-256 de ce fichier est consignee dans l'application sous cette",
                        "reference. Elle n'y figure pas : l'y ecrire changerait les octets du fichier,",
                        "donc l'empreinte elle-meme. Pour verifier, deposez ce fichier dans l'ecran",
                        "d'audit : l'application recalcule son empreinte et indique s'il correspond.")) {
                    ecrire(cs, police, 9, 40, yc, mention);
                    yc -= 14;
                }
            } finally {
                if (cs != null) cs.close();
            }
            MetadonneesPdf.appliquer(document, titre, auteur);
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Le classeur, et sa feuille de provenance.
     *
     * <p>Le PDF porte son colophon en derniere page ; le classeur n'en avait aucun. Or un fichier se
     * renomme, et un tableur detache de son nom ne dit plus ni ce qu'il couvre, ni qui l'a extrait,
     * ni sous quelle reference il est consigne. La matiere qu'on trie a autant besoin de sa
     * provenance que la piece qu'on remet.
     */
    public byte[] excel(List<LigneExport> lignes, LocalDate dateDebut, LocalDate dateFin,
                        String titre, String auteur) {
        try (XSSFWorkbook classeur = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet feuille = classeur.createSheet("Audit");
            Row entete = feuille.createRow(0);
            for (int i = 0; i < EN_TETES.length; i++) {
                entete.createCell(i).setCellValue(EN_TETES[i]);
            }
            int index = 1;
            for (LigneExport l : lignes) {
                Row ligne = feuille.createRow(index++);
                ligne.createCell(0).setCellValue(l.horodatage());
                ligne.createCell(1).setCellValue(l.auteur());
                ligne.createCell(2).setCellValue(l.dossier());
                ligne.createCell(3).setCellValue(l.action());
                ligne.createCell(4).setCellValue(l.details());
            }
            /*
             * `autoSizeColumn` parcourt toutes les lignes pour chacune des colonnes : sur une
             * extraction de plusieurs milliers d'entrées, il coûte plus cher que l'écriture
             * elle-même. Largeurs fixes, généreuses sur les deux colonnes longues.
             */
            int[] largeurs = {4_500, 6_000, 4_000, 12_000, 20_000};
            for (int i = 0; i < largeurs.length; i++) {
                feuille.setColumnWidth(i, largeurs[i]);
            }
            /*
             * Feuille de provenance, en seconde position : elle ne doit pas s'interposer entre celui
             * qui ouvre le classeur et les lignes qu'il vient chercher.
             */
            XSSFSheet provenance = classeur.createSheet("Provenance");
            String[] mentions = {
                    titre == null ? "" : titre,
                    "Periode couverte : du " + dateDebut + " au " + dateFin,
                    "Lignes : " + lignes.size(),
                    "Extrait par : " + (auteur == null || auteur.isBlank() ? "-" : auteur),
                    "Extrait le : " + horodater(java.time.LocalDateTime.now()),
                    "",
                    "L'empreinte SHA-256 de ce fichier est consignee dans l'application sous cette reference.",
                    "Elle n'y figure pas : l'y ecrire changerait les octets du fichier, donc l'empreinte.",
                    "Pour verifier, deposez ce fichier dans l'ecran d'audit.",
            };
            for (int i = 0; i < mentions.length; i++) {
                provenance.createRow(i).createCell(0).setCellValue(mentions[i]);
            }
            provenance.setColumnWidth(0, 24_000);

            classeur.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Horodatage lisible, identique dans les deux formats. */
    public static String horodater(java.time.LocalDateTime instant) {
        return instant == null ? "" : instant.format(HORODATAGE);
    }

    /** Détails mis à plat : `cle = valeur`, séparés par des points-virgules. */
    public static String aplatir(Map<String, Object> details) {
        if (details == null || details.isEmpty()) return "";
        return details.entrySet().stream()
                .map(e -> e.getKey() + " = " + (e.getValue() == null ? "" : e.getValue()))
                .collect(Collectors.joining(" ; "));
    }

    private void ecrire(PDPageContentStream cs, PDType1Font police, float taille, float x, float y, String texte)
            throws IOException {
        cs.setFont(police, taille);
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(TextePdf.replier(texte));
        cs.endText();
    }

    private static String ligneTexte(String date, String auteur, String dossier, String action, String details) {
        return String.format("%-17s %-22s %-10s %-42s %s",
                tronquer(date, 17), tronquer(auteur, 22), tronquer(dossier, 10),
                tronquer(action, 42), tronquer(details, 60));
    }

    private static String tronquer(String texte, int longueur) {
        if (texte == null) return "";
        return texte.length() <= longueur ? texte : texte.substring(0, longueur - 1) + ".";
    }

    /** Refus explicite plutôt qu'un fichier tronqué : cf. {@code AuditExportService}. */
    static ApiException extractionTropVaste(int plafond) {
        return ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                "L'extraction dépasse " + plafond + " lignes. Restreignez la période ou les filtres : "
                        + "au-delà, le fichier cesse d'être un document et devient un déversement de données.");
    }
}
