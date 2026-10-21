package com.afriland.audiences.reporting.service;

import com.afriland.audiences.common.pdf.DocumentPdf;
import com.afriland.audiences.common.pdf.LibellesPdf;
import com.afriland.audiences.reporting.web.dto.RapportResponse;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Periodic report exports (FR-REP-01): PDF, Excel and CSV (JSON is handled by the controller).
 * Laid out as label/value pairs, since the report is a single aggregate.
 */
@Service
public class RapportExportService {

    /** Two columns over the 515 usable points of a portrait A4 page. */
    private static final float[] LARGEURS = {330f, 185f};

    /**
     * The report, laid out with the shared PDF template, which handles page breaks.
     *
     * @param auteur requester, shown in the footer
     * @param langue label language
     */
    public byte[] exportPdf(RapportResponse rapport, String auteur, Locale langue) {
        LibellesPdf libelles = LibellesPdf.pour(langue);
        try (DocumentPdf document = new DocumentPdf(PDRectangle.A4,
                libelles.texte("rapport.titre", rapport.type()),
                libelles.texte("rapport.periode", rapport.dateDebut(), rapport.dateFin()),
                auteur, langue)) {
            document.nouvellePage();
            document.tableau(
                    List.of(libelles.texte("rapport.colonne.indicateur"), libelles.texte("rapport.colonne.valeur")),
                    LARGEURS,
                    indicateurs(rapport, libelles).entrySet().stream()
                            .map(entree -> List.of(entree.getKey(), entree.getValue()))
                            .toList());
            return document.finaliser();
        }
    }

    /** For callers without an author at hand. */
    public byte[] exportPdf(RapportResponse rapport) {
        return exportPdf(rapport, null, null);
    }

    public byte[] exportExcel(RapportResponse rapport) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Rapport " + rapport.type());
            Row entete = sheet.createRow(0);
            entete.createCell(0).setCellValue("Indicateur");
            entete.createCell(1).setCellValue("Valeur");
            int rowIndex = 1;
            for (Map.Entry<String, String> entree : indicateurs(rapport).entrySet()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(entree.getKey());
                row.createCell(1).setCellValue(entree.getValue());
            }
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Échec de la génération du rapport Excel", e);
        }
    }

    public byte[] exportCsv(RapportResponse rapport) {
        StringBuilder csv = new StringBuilder("Indicateur,Valeur\n");
        indicateurs(rapport).forEach((cle, valeur) -> csv.append(cle).append(',').append(valeur).append('\n'));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** The indicators in reading order, with labels from the same source as the PDF. */
    private Map<String, String> indicateurs(RapportResponse rapport, LibellesPdf libelles) {
        Map<String, String> indicateurs = new LinkedHashMap<>();
        indicateurs.put(libelles.texte("rapport.indicateur.nouveauxDossiers"),
                String.valueOf(rapport.nbNouveauxDossiers()));
        indicateurs.put(libelles.texte("rapport.indicateur.audiencesTenues"),
                String.valueOf(rapport.nbAudiencesTenues()));
        indicateurs.put(libelles.texte("rapport.indicateur.fraisDeposees"),
                String.valueOf(rapport.nbDemandesFraisDeposees()));
        indicateurs.put(libelles.texte("rapport.indicateur.deliberesEnregistres"),
                String.valueOf(rapport.nbDeliberesEnregistres()));
        // Counts rather than the success rate: a percentage hides how many cases it covers.
        indicateurs.put(libelles.texte("rapport.indicateur.litigesGagnes"),
                String.valueOf(rapport.nbLitigesGagnes()));
        indicateurs.put(libelles.texte("rapport.indicateur.litigesPerdus"),
                String.valueOf(rapport.nbLitigesPerdus()));
        indicateurs.put(libelles.texte("rapport.indicateur.decisionsMixtes"),
                String.valueOf(rapport.nbLitigesMixtes()));
        indicateurs.put(libelles.texte("rapport.indicateur.provisionsReprises"),
                String.valueOf(rapport.provisionsReprises()));
        return indicateurs;
    }

    /**
     * French labels for Excel and CSV. These formats are not translated: they feed downstream
     * processing, where a column renamed between runs would break consumers.
     */
    private Map<String, String> indicateurs(RapportResponse rapport) {
        return indicateurs(rapport, LibellesPdf.pour(null));
    }
}
