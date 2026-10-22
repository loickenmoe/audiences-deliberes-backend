package com.afriland.audiences.reporting.service;

import com.afriland.audiences.common.pdf.DocumentPdf;
import com.afriland.audiences.reporting.web.dto.RapportActiviteResponse;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Activity sheet exports: PDF, Excel, CSV. Separate from {@link RapportExportService}, which lays
 * out a single label/value aggregate, whereas this is a variable-length list of rows.
 *
 * <p>Documents are in French: action type labels exist only in French in the backend enum. The UI
 * is bilingual.
 */
@Service
public class RapportActiviteExportService {

    /** Two columns over the 515 usable points of a portrait A4 page. */
    private static final float[] LARGEURS = {395f, 120f};

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String COLONNE_NATURE = "Nature de l'action";
    private static final String COLONNE_NOMBRE = "Nombre";

    /** The sheet, laid out with the shared PDF template, which handles page breaks. */
    public byte[] exportPdf(RapportActiviteResponse rapport) {
        try (DocumentPdf document = new DocumentPdf(PDRectangle.A4,
                "Activité de " + nom(rapport),
                "Du " + rapport.dateDebut().format(JOUR) + " au " + rapport.dateFin().format(JOUR),
                nom(rapport), null)) {
            document.nouvellePage();
            document.tableau(List.of(COLONNE_NATURE, COLONNE_NOMBRE), LARGEURS, lignes(rapport));
            return document.finaliser();
        }
    }

    public byte[] exportExcel(RapportActiviteResponse rapport) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Activité");
            Row entete = sheet.createRow(0);
            entete.createCell(0).setCellValue(COLONNE_NATURE);
            entete.createCell(1).setCellValue(COLONNE_NOMBRE);
            int rang = 1;
            for (List<String> ligne : lignes(rapport)) {
                Row row = sheet.createRow(rang++);
                row.createCell(0).setCellValue(ligne.get(0));
                row.createCell(1).setCellValue(ligne.get(1));
            }
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Échec de la génération de la fiche d'activité Excel", e);
        }
    }

    public byte[] exportCsv(RapportActiviteResponse rapport) {
        StringBuilder csv = new StringBuilder(COLONNE_NATURE + ";" + COLONNE_NOMBRE + "\n");
        for (List<String> ligne : lignes(rapport)) {
            csv.append(ligne.get(0)).append(';').append(ligne.get(1)).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Document rows in service order. The average delay comes last after a blank row, since it is
     * not an action count; it is omitted (not zeroed) when there is nothing to measure.
     */
    private List<List<String>> lignes(RapportActiviteResponse rapport) {
        List<List<String>> lignes = new java.util.ArrayList<>(rapport.lignes().stream()
                .map(ligne -> List.of(ligne.code().libelle(), String.valueOf(ligne.total())))
                .toList());
        if (rapport.delaiMoyenSaisieCompteRendu() != null) {
            lignes.add(List.of("", ""));
            lignes.add(List.of("Délai moyen de saisie d'un compte rendu",
                    String.format("%.1f j", rapport.delaiMoyenSaisieCompteRendu())));
        }
        return lignes;
    }

    /** The person's name, or their id as a fallback. */
    private static String nom(RapportActiviteResponse rapport) {
        return rapport.nom() == null ? "l'utilisateur #" + rapport.utilisateurId() : rapport.nom();
    }

    /** Strips accents first, otherwise accented letters fall outside {@code [a-z0-9]}. */
    private static String sansAccent(String nom) {
        return java.text.Normalizer.normalize(nom, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceFirst("^-", "")
                .replaceFirst("-$", "");
    }

    /** File name built from the person and the period, so downloaded sheets are distinguishable. */
    public String nomFichier(RapportActiviteResponse rapport, String extension) {
        String personne = rapport.nom() == null
                ? String.valueOf(rapport.utilisateurId())
                : sansAccent(rapport.nom());
        return "activite-" + personne + "-" + rapport.dateDebut() + "_" + rapport.dateFin() + "." + extension;
    }
}
