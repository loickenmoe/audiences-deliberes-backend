package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.pdf.DocumentPdf;
import com.afriland.audiences.common.pdf.TextePdf;
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

    /**
     * Largeur de chaque colonne, en points, sur les 762 utiles d'une A4 paysage. Les deux colonnes
     * longues — l'action et ses détails — reçoivent les deux tiers de la place : ce sont elles qu'on
     * lit, les trois autres ne servent qu'à situer.
     */
    private static final float[] LARGEURS = {80f, 115f, 75f, 190f, 302f};
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
    /**
     * L'archive d'audit, mise en page par la charte commune (Q-115 A3).
     *
     * <p><b>Ce que cette version corrige.</b> Les colonnes étaient calées par des espaces
     * ({@code String.format("%-17s %-22s …")}) dans une police <b>proportionnelle</b> : sur mille
     * cinq cents lignes, elles ondulaient. La charte positionne chaque colonne à son abscisse et
     * <b>replie</b> à la largeur mesurée : une cellule trop longue passe à la ligne au lieu d'être
     * coupée — la colonne « Détails » perdait ce qui dépassait, et une pièce d'audit n'a rien à
     * cacher à qui la lit.
     *
     * <p><b>Les archives déjà produites ne bougent pas.</b> Leurs octets dorment dans MinIO et leur
     * empreinte reste vérifiable : seules les extractions à venir prennent cette allure.
     *
     * <p><b>Le colophon est conservé</b>, et reste en dernière page. Il n'est pas décoratif : il
     * explique pourquoi l'empreinte ne peut pas figurer dans le fichier qu'elle scelle, et donne à
     * qui reçoit la pièce le moyen de la vérifier.
     *
     * <p><b>La langue reste le français, et c'est une décision.</b> Une archive d'audit est une
     * pièce, remise à un contrôleur : elle garde la langue de la procédure, là où le calendrier et
     * le rapport suivent leur lecteur. C'est aussi ce qui évite de porter côté serveur la table des
     * quarante-six codes d'action, dont la traduction vit côté client depuis QF-24.
     *
     * @param titre  porte la référence de l'archive, seule chose l'identifiant que le fichier puisse
     *               contenir : tout ce qui décrit son contenu — l'empreinte d'abord — changerait les
     *               octets en y étant écrit, donc changerait ce qu'il décrit
     * @param auteur nom de qui a demandé l'extraction, porté par le pied de page
     */
    public byte[] pdf(List<LigneExport> lignes, LocalDate dateDebut, LocalDate dateFin, String titre,
                      String auteur) {
        String periode = "Période du " + dateDebut + " au " + dateFin + " - " + lignes.size() + " ligne(s)";
        try (DocumentPdf document = new DocumentPdf(DocumentPdf.paysage(), titre, periode, auteur, null)) {
            document.nouvellePage();
            if (lignes.isEmpty()) {
                // Une page qui le dit vaut mieux qu'un PDF vide, qu'on prendrait pour un fichier
                // corrompu — et une archive vide reste une archive : elle atteste qu'il n'y a rien.
                document.ligne("Aucune action sur la periode du " + dateDebut + " au " + dateFin + ".", false, 10f);
            } else {
                document.tableau(List.of(EN_TETES), LARGEURS,
                        lignes.stream().map(AuditExportRenderer::cellules).toList(), true);
            }
            colophon(document, dateDebut, dateFin, lignes.size(), auteur);
            return document.finaliser();
        }
    }

    private static List<String> cellules(LigneExport ligne) {
        return List.of(ligne.horodatage(), ligne.auteur(), ligne.dossier(), ligne.action(), ligne.details());
    }

    /**
     * La dernière page, qui dit ce qu'est le fichier qu'on tient.
     *
     * <p>Un document d'audit détaché de l'application doit se décrire lui-même : sans quoi une page
     * imprimée devient anonyme, et il faudrait recalculer son empreinte rien que pour savoir de quoi
     * l'on parle.
     */
    private void colophon(DocumentPdf document, LocalDate dateDebut, LocalDate dateFin, int lignes, String auteur) {
        document.nouvellePage();
        for (String mention : List.of(
                "Periode couverte  : du " + dateDebut + " au " + dateFin,
                "Lignes            : " + lignes,
                "Extrait par       : " + (auteur == null || auteur.isBlank() ? "-" : auteur),
                "Extrait le        : " + horodater(java.time.LocalDateTime.now()),
                "",
                "L'empreinte SHA-256 de ce fichier est consignee dans l'application sous cette",
                "reference. Elle n'y figure pas : l'y ecrire changerait les octets du fichier,",
                "donc l'empreinte elle-meme. Pour verifier, deposez ce fichier dans l'ecran",
                "d'audit : l'application recalcule son empreinte et indique s'il correspond.")) {
            document.ligne(mention, false, 9f);
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

    /** Refus explicite plutôt qu'un fichier tronqué : cf. {@code AuditExportService}. */
    static ApiException extractionTropVaste(int plafond) {
        return ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                "L'extraction dépasse " + plafond + " lignes. Restreignez la période ou les filtres : "
                        + "au-delà, le fichier cesse d'être un document et devient un déversement de données.");
    }
}
