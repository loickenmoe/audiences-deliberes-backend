package com.afriland.audiences.reporting.web;

import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.referentiel.domain.CategorieDossier;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.reporting.domain.IndicateurTableauDeBord;
import com.afriland.audiences.reporting.domain.TypeRapport;
import com.afriland.audiences.reporting.service.DetailTableauDeBordService;
import com.afriland.audiences.reporting.service.JournalAudiencesService;
import com.afriland.audiences.reporting.service.RapportActiviteExportService;
import com.afriland.audiences.reporting.service.RapportActiviteService;
import com.afriland.audiences.reporting.service.RapportExportService;
import com.afriland.audiences.reporting.service.RapportNominatifService;
import com.afriland.audiences.reporting.service.ReportingService;
import com.afriland.audiences.reporting.web.dto.ActionDetailleeResponse;
import com.afriland.audiences.reporting.web.dto.AudienceDuJourResponse;
import com.afriland.audiences.reporting.web.dto.CompteRenduAttenduResponse;
import com.afriland.audiences.reporting.web.dto.DetailIndicateurResponse;
import com.afriland.audiences.reporting.web.dto.FormatRapport;
import com.afriland.audiences.reporting.web.dto.RapportActiviteResponse;
import com.afriland.audiences.reporting.web.dto.RapportAvocatsResponse;
import com.afriland.audiences.reporting.web.dto.RapportNominatifResponse;
import com.afriland.audiences.reporting.web.dto.RapportResponse;
import com.afriland.audiences.reporting.web.dto.TableauDeBordResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Reporting endpoints: what each one returns for an empty period (204), each export format with
 * its download headers, and the export footer when the requester cannot be named. Authorization
 * ({@code @PreAuthorize}) is covered by the HTTP integration tests.
 */
@ExtendWith(MockitoExtension.class)
class ReportingControllerTest {

    private static final LocalDate DEBUT = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 9, 30);
    private static final byte[] OCTETS = {1, 2, 3};

    @Mock private ReportingService reportingService;
    @Mock private RapportExportService rapportExportService;
    @Mock private RapportNominatifService rapportNominatifService;
    @Mock private RapportActiviteService rapportActiviteService;
    @Mock private DetailTableauDeBordService detailTableauDeBordService;
    @Mock private RapportActiviteExportService rapportActiviteExportService;
    @Mock private JournalAudiencesService journalAudiencesService;
    @Mock private CurrentUserService currentUserService;

    @InjectMocks private ReportingController controller;

    private static <T> T rapport(Class<T> type, boolean vide) {
        T rapport = mock(type);
        if (type == RapportResponse.class) {
            when(((RapportResponse) rapport).estVide()).thenReturn(vide);
        } else if (type == RapportNominatifResponse.class) {
            when(((RapportNominatifResponse) rapport).estVide()).thenReturn(vide);
        } else if (type == RapportAvocatsResponse.class) {
            when(((RapportAvocatsResponse) rapport).estVide()).thenReturn(vide);
        } else {
            when(((RapportActiviteResponse) rapport).estVide()).thenReturn(vide);
        }
        return rapport;
    }

    @Test
    void tableauDeBord_etSonDetail() {
        TableauDeBordResponse tableau = mock(TableauDeBordResponse.class);
        when(reportingService.tableauDeBord(CategorieDossier.RECOUVREMENT)).thenReturn(tableau);
        DetailIndicateurResponse ligne = new DetailIndicateurResponse(1L, 1L, "DOS-1", "Client", null, null, null);
        when(detailTableauDeBordService.detailler(IndicateurTableauDeBord.DOSSIERS_ACTIFS, null, PageRequest.of(2, 5)))
                .thenReturn(new PageImpl<>(List.of(ligne), PageRequest.of(2, 5), 11));

        assertThat(controller.tableauDeBord(CategorieDossier.RECOUVREMENT)).isSameAs(tableau);
        PageResponse<DetailIndicateurResponse> page =
                controller.detailTableauDeBord(IndicateurTableauDeBord.DOSSIERS_ACTIFS, null, 2, 5);
        assertThat(page.content()).containsExactly(ligne);
        assertThat(page.totalElements()).isEqualTo(11);
    }

    @Test
    void journalEtComptesRendusAttendus_sontTriesEtPagines() {
        when(journalAudiencesService.journal(eq(DEBUT), any())).thenReturn(PageResponse.of(new PageImpl<AudienceDuJourResponse>(List.of())));
        when(journalAudiencesService.comptesRendusAttendus(eq(DEBUT), eq(FIN), any()))
                .thenReturn(PageResponse.of(new PageImpl<CompteRenduAttenduResponse>(List.of())));

        controller.journalDesAudiences(DEBUT, 1, 10);
        controller.comptesRendusAttendus(DEBUT, FIN, 0, 10);

        verify(journalAudiencesService).journal(DEBUT, PageRequest.of(1, 10, Sort.by("id")));
        verify(journalAudiencesService).comptesRendusAttendus(DEBUT, FIN, PageRequest.of(0, 10, Sort.by("datePlanifiee", "id")));
    }

    @Test
    void rapport_videRenvoie204SansExport() {
        RapportResponse vide = rapport(RapportResponse.class, true);
        when(reportingService.genererRapport(TypeRapport.MENSUEL, DEBUT, FIN, null)).thenReturn(vide);

        ResponseEntity<Object> reponse = controller.rapports(TypeRapport.MENSUEL, DEBUT, FIN, null, FormatRapport.PDF);

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verifyNoInteractions(rapportExportService);
    }

    @Test
    void rapport_enJson() {
        RapportResponse plein = rapport(RapportResponse.class, false);
        when(reportingService.genererRapport(TypeRapport.MENSUEL, DEBUT, FIN, CategorieDossier.RECOUVREMENT)).thenReturn(plein);

        ResponseEntity<Object> reponse =
                controller.rapports(TypeRapport.MENSUEL, DEBUT, FIN, CategorieDossier.RECOUVREMENT, FormatRapport.JSON);

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reponse.getBody()).isSameAs(plein);
    }

    @Test
    void rapport_enPdf_signeDuNomDuDemandeur() {
        RapportResponse plein = rapport(RapportResponse.class, false);
        when(reportingService.genererRapport(any(), any(), any(), any())).thenReturn(plein);
        when(currentUserService.getCurrentUser()).thenReturn(Utilisateur.builder().prenom("Marie").nom("Directrice").build());
        when(rapportExportService.exportPdf(plein, "Marie Directrice", null)).thenReturn(OCTETS);

        ResponseEntity<Object> reponse = controller.rapports(TypeRapport.MENSUEL, DEBUT, FIN, null, FormatRapport.PDF);

        assertThat(reponse.getBody()).isEqualTo(OCTETS);
        assertThat(reponse.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("attachment; filename=rapport.pdf");
        assertThat(reponse.getHeaders().getContentType()).hasToString("application/pdf");
    }

    @Test
    void rapport_enPdf_sansDemandeurIdentifiable() {
        RapportResponse plein = rapport(RapportResponse.class, false);
        when(reportingService.genererRapport(any(), any(), any(), any())).thenReturn(plein);
        when(currentUserService.getCurrentUser()).thenReturn(null).thenThrow(new IllegalStateException("pas de session"));
        when(rapportExportService.exportPdf(eq(plein), isNull(), isNull())).thenReturn(OCTETS);

        // Neither a missing user nor a failing lookup prevents the document: the footer shows only the date.
        assertThat(controller.rapports(TypeRapport.MENSUEL, DEBUT, FIN, null, FormatRapport.PDF).getBody()).isEqualTo(OCTETS);
        assertThat(controller.rapports(TypeRapport.MENSUEL, DEBUT, FIN, null, FormatRapport.PDF).getBody()).isEqualTo(OCTETS);
    }

    @ParameterizedTest
    @CsvSource({
            "EXCEL, rapport.xlsx, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "CSV, rapport.csv, text/csv"})
    void rapport_enExcelEtCsv(FormatRapport format, String fichier, String type) {
        RapportResponse plein = rapport(RapportResponse.class, false);
        when(reportingService.genererRapport(any(), any(), any(), any())).thenReturn(plein);
        // One format per run: the other export stub is unused, hence lenient.
        lenient().when(rapportExportService.exportExcel(plein)).thenReturn(OCTETS);
        lenient().when(rapportExportService.exportCsv(plein)).thenReturn(OCTETS);

        ResponseEntity<Object> reponse = controller.rapports(TypeRapport.HEBDOMADAIRE, DEBUT, FIN, null, format);

        assertThat(reponse.getBody()).isEqualTo(OCTETS);
        assertThat(reponse.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("attachment; filename=" + fichier);
        assertThat(reponse.getHeaders().getContentType()).hasToString(type);
    }

    @Test
    void rapportsNominatifs_videOuPlein() {
        RapportNominatifResponse internesPleins = rapport(RapportNominatifResponse.class, false);
        RapportAvocatsResponse avocatsVides = rapport(RapportAvocatsResponse.class, true);
        when(rapportNominatifService.produire(DEBUT, FIN)).thenReturn(internesPleins);
        when(rapportNominatifService.produireAvocats(DEBUT, FIN)).thenReturn(avocatsVides);

        assertThat(controller.rapportNominatif(DEBUT, FIN).getBody()).isSameAs(internesPleins);
        assertThat(controller.rapportAvocats(DEBUT, FIN).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void rapportsNominatifs_inverses() {
        RapportNominatifResponse internesVides = rapport(RapportNominatifResponse.class, true);
        RapportAvocatsResponse avocatsPleins = rapport(RapportAvocatsResponse.class, false);
        when(rapportNominatifService.produire(DEBUT, FIN)).thenReturn(internesVides);
        when(rapportNominatifService.produireAvocats(DEBUT, FIN)).thenReturn(avocatsPleins);

        assertThat(controller.rapportNominatif(DEBUT, FIN).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(controller.rapportAvocats(DEBUT, FIN).getBody()).isSameAs(avocatsPleins);
    }

    @Test
    void ficheActivite_videOuPleine_etSonDetail() {
        RapportActiviteResponse pleine = rapport(RapportActiviteResponse.class, false);
        RapportActiviteResponse vide = rapport(RapportActiviteResponse.class, true);
        when(rapportActiviteService.produire(4L, null, DEBUT, FIN)).thenReturn(pleine);
        when(rapportActiviteService.produire(null, 9L, DEBUT, FIN)).thenReturn(vide);
        when(rapportActiviteService.detailler(4L, null, TypeActionHistorique.DOSSIER_CREE, DEBUT, FIN, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<ActionDetailleeResponse>(List.of()));

        assertThat(controller.activite(DEBUT, FIN, 4L, null).getBody()).isSameAs(pleine);
        assertThat(controller.activite(DEBUT, FIN, null, 9L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(controller.activiteDetails(DEBUT, FIN, TypeActionHistorique.DOSSIER_CREE, 4L, null, 0, 20).content()).isEmpty();
    }

    @Test
    void exportActivite_videRenvoie204_etJsonEstRefuse() {
        RapportActiviteResponse vide = rapport(RapportActiviteResponse.class, true);
        RapportActiviteResponse pleine = rapport(RapportActiviteResponse.class, false);
        when(rapportActiviteService.produire(1L, null, DEBUT, FIN)).thenReturn(vide);
        when(rapportActiviteService.produire(2L, null, DEBUT, FIN)).thenReturn(pleine);

        assertThat(controller.activiteExport(DEBUT, FIN, 1L, null, FormatRapport.PDF).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThatThrownBy(() -> controller.activiteExport(DEBUT, FIN, 2L, null, FormatRapport.JSON))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("/rapports/activite");
    }

    @ParameterizedTest
    @CsvSource({
            "PDF, pdf, application/pdf",
            "EXCEL, xlsx, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "CSV, csv, text/csv"})
    void exportActivite_parFormat(FormatRapport format, String extension, String type) {
        RapportActiviteResponse pleine = rapport(RapportActiviteResponse.class, false);
        when(rapportActiviteService.produire(null, 9L, DEBUT, FIN)).thenReturn(pleine);
        // One format per run: the other export stubs are unused, hence lenient.
        lenient().when(rapportActiviteExportService.exportPdf(pleine)).thenReturn(OCTETS);
        lenient().when(rapportActiviteExportService.exportExcel(pleine)).thenReturn(OCTETS);
        lenient().when(rapportActiviteExportService.exportCsv(pleine)).thenReturn(OCTETS);
        when(rapportActiviteExportService.nomFichier(eq(pleine), anyString()))
                .thenAnswer(i -> "activite-me-fotso-2026-09." + i.getArgument(1));

        ResponseEntity<byte[]> reponse = controller.activiteExport(DEBUT, FIN, null, 9L, format);

        assertThat(reponse.getBody()).isEqualTo(OCTETS);
        assertThat(reponse.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"activite-me-fotso-2026-09." + extension + "\"");
        assertThat(reponse.getHeaders().getContentType()).hasToString(type);
    }
}
