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
 * Renders an audit export (a flat list of lines) as PDF or Excel.
 *
 * <p>The PDF uses Helvetica, which does not cover all of Unicode; character fallback is handled by
 * {@link com.afriland.audiences.common.pdf.TextePdf}.
 */
@Service
public class AuditExportRenderer {

    private static final String[] EN_TETES = {"Date", "Auteur", "Dossier", "Action", "Détails"};

    /** Column widths in points (762 usable on landscape A4); action and details get two thirds. */
    private static final float[] LARGEURS = {80f, 115f, 75f, 190f, 302f};
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** A ready-to-write line: author already resolved and details flattened by the service. */
    public record LigneExport(String horodatage, String auteur, String dossier, String action, String details) {
    }

    /**
     * The audit archive, laid out with the shared PDF charter. Columns are positioned at fixed
     * offsets and long cells wrap instead of being truncated. The last page is a colophon explaining
     * how to verify the file. The archive always stays in French (the language of the procedure).
     *
     * @param titre  carries the archive reference, the only identifier the file can contain: writing
     *               its hash into it would change the bytes, hence the hash
     * @param auteur name of the requester, printed in the footer
     */
    public byte[] pdf(List<LigneExport> lignes, LocalDate dateDebut, LocalDate dateFin, String titre,
                      String auteur) {
        String periode = "Période du " + dateDebut + " au " + dateFin + " - " + lignes.size() + " ligne(s)";
        try (DocumentPdf document = new DocumentPdf(DocumentPdf.paysage(), titre, periode, auteur, null)) {
            document.nouvellePage();
            if (lignes.isEmpty()) {
                // An explicit message rather than a blank PDF that would look corrupted; an empty
                // archive still attests that there was nothing.
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

    /** Last page describing the file, so a detached or printed copy remains self-describing. */
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
     * The workbook, with a "Provenance" sheet (the Excel counterpart of the PDF colophon) stating the
     * period, extractor and reference, since a renamed file would otherwise lose that context.
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
             * Fixed widths: `autoSizeColumn` scans every row per column and costs more than the
             * writing itself on large exports.
             */
            int[] largeurs = {4_500, 6_000, 4_000, 12_000, 20_000};
            for (int i = 0; i < largeurs.length; i++) {
                feuille.setColumnWidth(i, largeurs[i]);
            }
            /* Provenance sheet comes second so the data sheet opens first. */
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

    /** Human-readable timestamp, identical in both formats. */
    public static String horodater(java.time.LocalDateTime instant) {
        return instant == null ? "" : instant.format(HORODATAGE);
    }

    /** Flattens details as `key = value` pairs separated by semicolons. */
    public static String aplatir(Map<String, Object> details) {
        if (details == null || details.isEmpty()) return "";
        return details.entrySet().stream()
                .map(e -> e.getKey() + " = " + (e.getValue() == null ? "" : e.getValue()))
                .collect(Collectors.joining(" ; "));
    }

    /** Explicit rejection rather than a truncated file; see {@code AuditExportService}. */
    static ApiException extractionTropVaste(int plafond) {
        return ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                "L'extraction dépasse " + plafond + " lignes. Restreignez la période ou les filtres : "
                        + "au-delà, le fichier cesse d'être un document et devient un déversement de données.");
    }
}
