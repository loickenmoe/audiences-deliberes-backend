package com.afriland.audiences.reporting.service;

import com.afriland.audiences.common.pdf.MetadonneesPdf;
import com.afriland.audiences.common.pdf.TextePdf;
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
 * Exports FR-REP-01 (rapport périodique). Le format JSON est géré directement par le contrôleur ;
 * ce service couvre PDF (PDFBox), Excel (POI) et CSV — présentation en libellé/valeur, le rapport
 * étant un agrégat unique et non une liste de lignes (contrairement au calendrier, Épic 2).
 */
@Service
public class RapportExportService {

    /** Deux colonnes sur les 515 points utiles d'une A4 portrait : le libellé prime sur la valeur. */
    private static final float[] LARGEURS = {330f, 185f};

    /**
     * Le rapport, mis en page par la charte commune (Q-115 A2).
     *
     * <p>Il tenait sur une page écrite à la main, sans garde de pagination : six indicateurs
     * passaient, trente auraient débordé hors de la feuille sans la moindre erreur. La charte
     * franchit les pages d'elle-même.
     *
     * @param auteur qui demande le rapport ; porté par le pied de page
     * @param langue langue des libellés — le rapport est un document de travail
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

    /** Conservé pour les appels qui n'ont pas d'auteur sous la main. */
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

    /**
     * Les six indicateurs, dans l'ordre où ils se lisent.
     *
     * <p>Les libellés viennent du même fichier que ceux du PDF : une seule source, sinon l'Excel et
     * le PDF finiraient par nommer différemment la même chose.
     */
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
        /*
         * Des nombres, pas un pourcentage (métier, 2026-10-06). Le taux était la seule ligne du
         * rapport exporté qui demandait un calcul mental pour être comprise : 71,88 % ne dit pas
         * sur combien d'affaires il porte. Les trois décomptes le disent.
         */
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
     * Version française, pour l'Excel et le CSV.
     *
     * <p>Ces deux formats ne sont pas traduits : ils alimentent des retraitements et des classeurs
     * existants, où renommer une colonne d'une exécution à l'autre casserait ce qui les consomme.
     */
    private Map<String, String> indicateurs(RapportResponse rapport) {
        return indicateurs(rapport, LibellesPdf.pour(null));
    }
}
