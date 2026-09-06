package com.afriland.audiences.audience.service;

import com.afriland.audiences.audience.web.dto.CalendrierEntreeResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Exports FR-AUD-05 (calendrier des audiences). Le format JSON est géré directement par le
 * contrôleur (sérialisation Jackson standard) ; ce service ne couvre que PDF (PDFBox, Apache 2.0
 * — cf. Q-31) et Excel (Apache POI).
 */
@Service
public class CalendrierExportService {

    private static final String[] EN_TETES = {
            "Référence", "Juridiction", "Étape", "Client", "Agence d'origine", "Risque encouru", "Date"
    };
    private static final float LIGNE_HAUTEUR = 16f;
    private static final int LIGNES_PAR_PAGE = 40;

    public byte[] exportPdf(List<CalendrierEntreeResponse> entrees) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<List<CalendrierEntreeResponse>> pages = decouperEnPages(entrees, LIGNES_PAR_PAGE);
            if (pages.isEmpty()) {
                pages = List.of(List.of());
            }
            for (List<CalendrierEntreeResponse> page : pages) {
                ecrirePage(document, page);
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Échec de la génération du calendrier PDF", e);
        }
    }

    private static final PDRectangle A4_PAYSAGE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());

    private void ecrirePage(PDDocument document, List<CalendrierEntreeResponse> lignes) throws IOException {
        PDPage page = new PDPage(A4_PAYSAGE);
        document.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
            float x = 30, y = page.getMediaBox().getHeight() - 40;
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
            y = ecrireLigne(cs, x, y, EN_TETES);
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
            for (CalendrierEntreeResponse entree : lignes) {
                y = ecrireLigne(cs, x, y, new String[]{
                        vide(entree.referenceDossier()), vide(entree.juridiction()), vide(entree.etape()),
                        vide(entree.clientNom()), vide(entree.codeAgenceOrigine()),
                        entree.risqueEncouru() == null ? "" : String.valueOf(entree.risqueEncouru()),
                        entree.datePlanifiee() == null ? "" : entree.datePlanifiee().toString()
                });
            }
        }
    }

    private float ecrireLigne(PDPageContentStream cs, float x, float y, String[] colonnes) throws IOException {
        float largeurColonne = 110f;
        cs.beginText();
        cs.newLineAtOffset(x, y);
        for (int i = 0; i < colonnes.length; i++) {
            if (i > 0) {
                cs.newLineAtOffset(largeurColonne, 0);
            }
            cs.showText(tronquer(colonnes[i]));
        }
        cs.endText();
        return y - LIGNE_HAUTEUR;
    }

    private String tronquer(String valeur) {
        return valeur.length() > 18 ? valeur.substring(0, 17) + "…" : valeur;
    }

    private String vide(String valeur) {
        return valeur == null ? "" : valeur;
    }

    private List<List<CalendrierEntreeResponse>> decouperEnPages(List<CalendrierEntreeResponse> entrees, int taille) {
        return entrees.isEmpty() ? List.of()
                : java.util.stream.IntStream.range(0, (entrees.size() + taille - 1) / taille)
                .mapToObj(i -> entrees.subList(i * taille, Math.min(entrees.size(), (i + 1) * taille)))
                .toList();
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
