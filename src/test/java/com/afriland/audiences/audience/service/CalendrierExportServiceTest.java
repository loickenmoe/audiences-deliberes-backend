package com.afriland.audiences.audience.service;

import com.afriland.audiences.audience.web.dto.CalendrierEntreeResponse;
import com.afriland.audiences.common.pdf.LecturePdf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalendrierExportServiceTest {

    private final CalendrierExportService service = new CalendrierExportService();

    private List<CalendrierEntreeResponse> entrees() {
        return List.of(new CalendrierEntreeResponse(1L, 2L, "DOS-2026-001", "Tribunal de Yaoundé", "INSTANCE",
                List.of(10L), List.of(20L), "AG01", "Client Test", 5_000_000d, LocalDate.now().plusDays(5)));
    }

    @Test
    void exportPdf_producesNonEmptyPdfBytes() {
        byte[] pdf = service.exportPdf(entrees());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void exportPdf_handlesEmptyList() {
        byte[] pdf = service.exportPdf(List.of());

        assertThat(pdf).isNotEmpty();
    }

    /**
     * Ce que le fichier **contient**, et non plus seulement qu'il en est un (A1). La version
     * precedente se contentait de « non vide, commence par %PDF- » : un calendrier qui aurait perdu
     * toutes ses lignes passait au vert.
     */
    @Test
    @DisplayName("le PDF porte les en-tetes et les donnees de chaque entree")
    void exportPdf_contientLesDonnees() throws Exception {
        String texte = LecturePdf.texte(service.exportPdf(entrees()));

        // Les sept en-têtes, **accents compris** : « Référence » et « Étape » passent par le repli de
        // TextePdf, et c'est précisément là qu'un caractère se perd sans qu'on le remarque.
        assertThat(texte).contains("Référence").contains("Juridiction").contains("Étape")
                .contains("Client").contains("Agence d'origine").contains("Risque encouru").contains("Date");
        assertThat(texte).contains("DOS-2026-001").contains("Client Test").contains("AG01");
        // La marque et la numérotation, que le document n'avait jamais portées (A2).
        assertThat(texte).contains("Afriland First Bank").contains("Direction Juridique");
        assertThat(texte).contains("Page 1 sur 1");
    }

    @Test
    @DisplayName("le fichier porte un titre, la ou il n'en avait aucun")
    void exportPdf_porteSesProprietes() throws Exception {
        // Le titre du fichier porte la période couverte, déduite des entrées : deux calendriers
        // extraits à deux semaines d'intervalle ne doivent pas se confondre dans un explorateur (A2).
        assertThat(LecturePdf.proprietes(service.exportPdf(entrees())).getTitle())
                .startsWith("Calendrier des audiences - Du ");
    }

    /**
     * Une juridiction ou un nom de client peut contenir n'importe quel caractere : sans repli, la
     * police leve et l'export echoue (A1).
     */
    @Test
    @DisplayName("une saisie hors encodage n'empeche pas l'export")
    void exportPdf_supporteUneSaisieHorsEncodage() {
        var exotique = List.of(new CalendrierEntreeResponse(1L, 2L, "DOS-2026-002", "Tribunal	Yaounde", "INSTANCE",
                List.of(10L), List.of(20L), "AG01", "Nguyen Thị", 5_000_000d, LocalDate.now()));

        assertThat(service.exportPdf(exotique)).isNotEmpty();
    }

    @Test
    void exportExcel_producesNonEmptyXlsxBytes() {
        byte[] xlsx = service.exportExcel(entrees());

        assertThat(xlsx).isNotEmpty();
        // Signature ZIP (xlsx = archive OOXML) : "PK".
        assertThat(xlsx[0]).isEqualTo((byte) 'P');
        assertThat(xlsx[1]).isEqualTo((byte) 'K');
    }
}
