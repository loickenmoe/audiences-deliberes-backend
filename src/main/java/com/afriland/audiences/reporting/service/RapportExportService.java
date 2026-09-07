package com.afriland.audiences.reporting.service;

import com.afriland.audiences.reporting.web.dto.RapportResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exports FR-REP-01 (rapport périodique). Le format JSON est géré directement par le contrôleur ;
 * ce service couvre PDF (PDFBox), Excel (POI) et CSV — présentation en libellé/valeur, le rapport
 * étant un agrégat unique et non une liste de lignes (contrairement au calendrier, Épic 2).
 */
@Service
public class RapportExportService {

    public byte[] exportPdf(RapportResponse rapport) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float x = 50, y = page.getMediaBox().getHeight() - 60;
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs.beginText();
                cs.newLineAtOffset(x, y);
                cs.showText("Rapport " + rapport.type() + " (" + rapport.dateDebut() + " - " + rapport.dateFin() + ")");
                cs.endText();
                y -= 30;
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                for (Map.Entry<String, String> entree : indicateurs(rapport).entrySet()) {
                    cs.beginText();
                    cs.newLineAtOffset(x, y);
                    cs.showText(entree.getKey() + " : " + entree.getValue());
                    cs.endText();
                    y -= 20;
                }
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Échec de la génération du rapport PDF", e);
        }
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

    private Map<String, String> indicateurs(RapportResponse rapport) {
        Map<String, String> indicateurs = new LinkedHashMap<>();
        indicateurs.put("Nouveaux dossiers", String.valueOf(rapport.nbNouveauxDossiers()));
        indicateurs.put("Audiences tenues", String.valueOf(rapport.nbAudiencesTenues()));
        indicateurs.put("Demandes de frais déposées", String.valueOf(rapport.nbDemandesFraisDeposees()));
        indicateurs.put("Délibérés enregistrés", String.valueOf(rapport.nbDeliberesEnregistres()));
        indicateurs.put("Taux de succès recouvrement", String.format("%.2f %%", rapport.tauxSuccesRecouvrement() * 100));
        indicateurs.put("Provisions reprises", String.valueOf(rapport.provisionsReprises()));
        return indicateurs;
    }
}
