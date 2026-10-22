package com.afriland.audiences.audience.service;

import com.afriland.audiences.audience.web.dto.CalendrierEntreeResponse;
import com.afriland.audiences.common.pdf.DocumentPdf;
import com.afriland.audiences.common.pdf.LibellesPdf;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * FR-AUD-05 hearing calendar exports. JSON is handled by the controller (standard Jackson
 * serialisation); this service covers PDF (PDFBox) and Excel (Apache POI).
 */
@Service
public class CalendrierExportService {

    private static final String[] EN_TETES = {
            "Référence", "Juridiction", "Étape", "Client", "Agence d'origine", "Risque encouru", "Date"
    };
    /**
     * Column widths in points. They add up to the 762 available in A4 landscape (842 minus two 40
     * margins), allocated by content rather than evenly.
     */
    private static final float[] LARGEURS = {96f, 150f, 110f, 150f, 80f, 90f, 86f};

    /**
     * The calendar, laid out with the shared document template (title, date, page numbers).
     *
     * @param auteur who requested the export; shown in the footer
     * @param langue label language: the calendar is a working document and follows its reader
     */
    public byte[] exportPdf(List<CalendrierEntreeResponse> entrees, String auteur, Locale langue) {
        LibellesPdf libelles = LibellesPdf.pour(langue);
        try (DocumentPdf document = new DocumentPdf(DocumentPdf.paysage(),
                libelles.texte("calendrier.titre"), periode(entrees, libelles), auteur, langue)) {
            document.nouvellePage();
            if (entrees.isEmpty()) {
                // An explicit "nothing in this period" page, so it is not mistaken for a broken
                // file.
                document.ligne(libelles.texte("calendrier.vide"), false, 10f);
            } else {
                document.tableau(entetes(libelles), LARGEURS, entrees.stream().map(this::cellules).toList());
            }
            return document.finaliser();
        }
    }

    /** For callers without an author at hand. */
    public byte[] exportPdf(List<CalendrierEntreeResponse> entrees) {
        return exportPdf(entrees, null, null);
    }

    private static List<String> entetes(LibellesPdf libelles) {
        return List.of(
                libelles.texte("calendrier.colonne.reference"),
                libelles.texte("calendrier.colonne.juridiction"),
                libelles.texte("calendrier.colonne.etape"),
                libelles.texte("calendrier.colonne.client"),
                libelles.texte("calendrier.colonne.agence"),
                libelles.texte("calendrier.colonne.risque"),
                libelles.texte("calendrier.colonne.date"));
    }

    private List<String> cellules(CalendrierEntreeResponse entree) {
        return List.of(
                vide(entree.referenceDossier()), vide(entree.juridiction()), vide(entree.etape()),
                vide(entree.clientNom()), vide(entree.codeAgenceOrigine()),
                entree.risqueEncouru() == null ? "" : String.valueOf(entree.risqueEncouru()),
                entree.datePlanifiee() == null ? "" : entree.datePlanifiee().toString());
    }

    /** The covered period, derived from the entries since the caller does not pass it. */
    private String periode(List<CalendrierEntreeResponse> entrees, LibellesPdf libelles) {
        List<LocalDate> dates = entrees.stream()
                .map(CalendrierEntreeResponse::datePlanifiee)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();
        return dates.isEmpty() ? null
                : libelles.texte("calendrier.periode", dates.get(0).toString(), dates.get(dates.size() - 1).toString());
    }

    private String vide(String valeur) {
        return valeur == null ? "" : valeur;
    }

    public byte[] exportExcel(List<CalendrierEntreeResponse> entrees) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Calendrier des audiences");
            Row entete = sheet.createRow(0);
            for (int i = 0; i < EN_TETES.length; i++) {
                entete.createCell(i).setCellValue(EN_TETES[i]);
            }
            int rowIndex = 1;
            for (CalendrierEntreeResponse entree : entrees) {
                Row row = sheet.createRow(rowIndex++);
                ecrireCellule(row, 0, entree.referenceDossier());
                ecrireCellule(row, 1, entree.juridiction());
                ecrireCellule(row, 2, entree.etape());
                ecrireCellule(row, 3, entree.clientNom());
                ecrireCellule(row, 4, entree.codeAgenceOrigine());
                if (entree.risqueEncouru() != null) {
                    row.createCell(5).setCellValue(entree.risqueEncouru());
                }
                ecrireCellule(row, 6, entree.datePlanifiee() == null ? null : entree.datePlanifiee().toString());
            }
            for (int i = 0; i < EN_TETES.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Échec de la génération du calendrier Excel", e);
        }
    }

    private void ecrireCellule(Row row, int index, String valeur) {
        Cell cell = row.createCell(index);
        if (valeur != null) {
            cell.setCellValue(valeur);
        }
    }
}
