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
 * Exports de la fiche d'activité (Q-133) : PDF, Excel, CSV.
 *
 * <p><b>Pourquoi un service à part de {@link RapportExportService}.</b> Celui-là met en page un
 * agrégat unique, en libellé/valeur ; celle-ci est une <b>liste de lignes</b> de longueur variable -
 * de une à quarante-neuf. Les deux formes ne se factorisent pas sans rendre les deux illisibles, et
 * toucher à l'export du rapport périodique pour servir un besoin nouveau risquerait un document que
 * la Direction imprime déjà.
 *
 * <p><b>Documents en français.</b> C'est le cas de tous les PDF du projet : il n'existe qu'un
 * {@code pdf/libelles.properties}, et le libellé d'une nature d'action n'a d'existence française que
 * dans l'énumération du backend - limite relevée en QF-24. L'écran, lui, est bilingue.
 */
@Service
public class RapportActiviteExportService {

    /** Deux colonnes sur les 515 points utiles d'une A4 portrait : la nature prime sur le nombre. */
    private static final float[] LARGEURS = {395f, 120f};

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String COLONNE_NATURE = "Nature de l'action";
    private static final String COLONNE_NOMBRE = "Nombre";

    /**
     * La fiche, mise en page par la charte commune (Q-115 A2) - qui franchit les pages d'elle-même :
     * une personne très active peut porter quarante-neuf lignes, et une page n'en tient pas autant.
     */
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
     * Les lignes du document, dans l'ordre où le service les a rendues.
     *
     * <p>Le <b>délai moyen</b> ferme la liste, précédé d'une ligne vide : ce n'est pas un décompte
     * d'actions, et le mêler aux autres laisserait croire qu'on peut l'additionner avec elles. Il
     * est omis - et non porté à zéro - quand il n'y a rien à mesurer.
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

    /** Le nom de la personne, ou son identifiant : un document doit dire de qui il parle. */
    private static String nom(RapportActiviteResponse rapport) {
        return rapport.nom() == null ? "l'utilisateur #" + rapport.utilisateurId() : rapport.nom();
    }

    /**
     * Les accents retirés avant la mise en minuscules : sans cette passe, « Hervé » donnait
     * « herv- », le caractère accentué ne tombant pas dans {@code [a-z0-9]}.
     */
    private static String sansAccent(String nom) {
        return java.text.Normalizer.normalize(nom, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    /**
     * Le nom du fichier, construit sur la personne et la période.
     *
     * <p>« activite.pdf » ne dit rien : une direction qui télécharge cinq fiches le même jour doit
     * pouvoir les distinguer sans les ouvrir.
     */
    public String nomFichier(RapportActiviteResponse rapport, String extension) {
        String personne = rapport.nom() == null
                ? String.valueOf(rapport.utilisateurId())
                : sansAccent(rapport.nom());
        return "activite-" + personne + "-" + rapport.dateDebut() + "_" + rapport.dateFin() + "." + extension;
    }
}
