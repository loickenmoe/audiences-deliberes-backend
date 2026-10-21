package com.afriland.audiences.reporting.web;

import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.referentiel.domain.CategorieDossier;
import com.afriland.audiences.reporting.domain.IndicateurTableauDeBord;
import com.afriland.audiences.reporting.domain.TypeRapport;
import com.afriland.audiences.reporting.service.DetailTableauDeBordService;
import com.afriland.audiences.reporting.service.JournalAudiencesService;
import com.afriland.audiences.reporting.service.RapportActiviteExportService;
import com.afriland.audiences.reporting.service.RapportActiviteService;
import com.afriland.audiences.reporting.service.RapportExportService;
import com.afriland.audiences.reporting.service.RapportNominatifService;
import com.afriland.audiences.reporting.service.ReportingService;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.reporting.web.dto.ActionDetailleeResponse;
import com.afriland.audiences.reporting.web.dto.AudienceDuJourResponse;
import com.afriland.audiences.reporting.web.dto.DetailIndicateurResponse;
import com.afriland.audiences.reporting.web.dto.RapportActiviteResponse;
import com.afriland.audiences.reporting.web.dto.RapportAvocatsResponse;
import com.afriland.audiences.reporting.web.dto.CompteRenduAttenduResponse;
import com.afriland.audiences.reporting.web.dto.FormatRapport;
import com.afriland.audiences.reporting.web.dto.RapportNominatifResponse;
import com.afriland.audiences.reporting.web.dto.RapportResponse;
import com.afriland.audiences.reporting.web.dto.TableauDeBordResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Reporting (FR-REP-01/02, US 8.4/8.5). US 8.6 (consolidated view per client) is exposed by
 * {@code GET /clients/{clientId}/dossiers} in {@code ClientController}.
 */
@RestController
@RequiredArgsConstructor
public class ReportingController {

    private final ReportingService reportingService;
    private final RapportExportService rapportExportService;
    private final RapportNominatifService rapportNominatifService;
    private final RapportActiviteService rapportActiviteService;
    private final DetailTableauDeBordService detailTableauDeBordService;
    private final RapportActiviteExportService rapportActiviteExportService;
    private final JournalAudiencesService journalAudiencesService;
    /** Used to name the requester in the export footer. */
    private final CurrentUserService currentUserService;

    /**
     * Open to DJ and DJA. The specification describes the consolidated dashboard as available to
     * both (STK-06): the DJA handles the same cases and co-validates sensitive fees.
     */
    @GetMapping("${api.prefix}/tableau-de-bord")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public TableauDeBordResponse tableauDeBord(@RequestParam(required = false) CategorieDossier categorie) {
        return reportingService.tableauDeBord(categorie);
    }

    /**
     * Hearing journal for a given day. Open to legal officers as well as management, since they
     * use it daily and write the reports themselves.
     */
    @GetMapping("${api.prefix}/audiences/journal")
    @PreAuthorize("hasAnyRole('JURISTE', 'DJ', 'DJA')")
    public PageResponse<AudienceDuJourResponse> journalDesAudiences(
            @RequestParam LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return journalAudiencesService.journal(date, PageRequest.of(page, size, Sort.by("id")));
    }

    /**
     * Past hearings still missing the legal officer's report. The window is mandatory so the list
     * stays bounded; oldest first, as they are the most urgent.
     */
    @GetMapping("${api.prefix}/audiences/comptes-rendus-attendus")
    @PreAuthorize("hasAnyRole('JURISTE', 'DJ', 'DJA')")
    public PageResponse<CompteRenduAttenduResponse> comptesRendusAttendus(
            @RequestParam LocalDate dateDebut,
            @RequestParam LocalDate dateFin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return journalAudiencesService.comptesRendusAttendus(dateDebut, dateFin,
                PageRequest.of(page, size, Sort.by("datePlanifiee", "id")));
    }

    @GetMapping("${api.prefix}/rapports")
    @PreAuthorize("hasRole('JURISTE')")
    public ResponseEntity<Object> rapports(@RequestParam TypeRapport type,
                                        @RequestParam LocalDate dateDebut,
                                        @RequestParam LocalDate dateFin,
                                        @RequestParam(required = false) CategorieDossier categorie,
                                        @RequestParam(defaultValue = "JSON") FormatRapport format) {
        RapportResponse rapport = reportingService.genererRapport(type, dateDebut, dateFin, categorie);
        if (rapport.estVide()) {
            return ResponseEntity.noContent().build();
        }
        return switch (format) {
            case JSON -> ResponseEntity.ok(rapport);
            case PDF -> ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=rapport.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(rapportExportService.exportPdf(rapport, nomDemandeur(), null));
            case EXCEL -> ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=rapport.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(rapportExportService.exportExcel(rapport));
            case CSV -> ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=rapport.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(rapportExportService.exportCsv(rapport));
        };
    }

    /**
     * Each user's activity over a period. Restricted to DJ/DJA by their own roles: this report names
     * people, and {@code ROLE_JURISTE} is composite, so granting it would expose colleagues'
     * activity to every legal officer. Returns 204 for an empty period.
     */
    @GetMapping("${api.prefix}/rapports/nominatif")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public ResponseEntity<RapportNominatifResponse> rapportNominatif(@RequestParam LocalDate dateDebut,
                                                                     @RequestParam LocalDate dateFin) {
        RapportNominatifResponse rapport = rapportNominatifService.produire(dateDebut, dateFin);
        return rapport.estVide() ? ResponseEntity.noContent().build() : ResponseEntity.ok(rapport);
    }

    /**
     * Detail behind a dashboard block, with the dashboard's own authorization. The detail returns
     * exactly what the counter counts, predicate for predicate (checked by an integration test).
     */
    @GetMapping("${api.prefix}/tableau-de-bord/details")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public PageResponse<DetailIndicateurResponse> detailTableauDeBord(
            @RequestParam IndicateurTableauDeBord indicateur,
            @RequestParam(required = false) CategorieDossier categorie,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(
                detailTableauDeBordService.detailler(indicateur, categorie, PageRequest.of(page, size)));
    }

    /**
     * Lawyers' activity over a period, counterpart of {@code /rapports/nominatif}, which counts
     * internal actions lawyers never perform. Same authorization as the internal report.
     */
    @GetMapping("${api.prefix}/rapports/nominatif/avocats")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public ResponseEntity<RapportAvocatsResponse> rapportAvocats(@RequestParam LocalDate dateDebut,
                                                                  @RequestParam LocalDate dateFin) {
        RapportAvocatsResponse rapport = rapportNominatifService.produireAvocats(dateDebut, dateFin);
        return rapport.estVide() ? ResponseEntity.noContent().build() : ResponseEntity.ok(rapport);
    }

    /**
     * A person's activity sheet: everyone on themselves, DJ/DJA on anyone. Lawyers are included
     * ({@code ROLE_AVOCAT}, as they do not hold {@code ROLE_CONSULTATION}).
     *
     * <p>The service decides whose activity may be read; {@code @PreAuthorize} cannot compare a
     * parameter with the caller. Returns 204 when the person did nothing in the period.
     */
    @GetMapping("${api.prefix}/rapports/activite")
    @PreAuthorize("hasAnyRole('CONSULTATION', 'AVOCAT')")
    public ResponseEntity<RapportActiviteResponse> activite(@RequestParam LocalDate dateDebut,
                                                            @RequestParam LocalDate dateFin,
                                                            @RequestParam(required = false) Long utilisateurId,
                                                            @RequestParam(required = false) Long avocatId) {
        RapportActiviteResponse rapport =
                rapportActiviteService.produire(utilisateurId, avocatId, dateDebut, dateFin);
        return rapport.estVide() ? ResponseEntity.noContent().build() : ResponseEntity.ok(rapport);
    }

    /**
     * The same sheet as a downloadable document. The file name carries the person and period.
     * JSON is rejected here: it is served by {@code /rapports/activite}.
     */
    @GetMapping("${api.prefix}/rapports/activite/export")
    @PreAuthorize("hasAnyRole('CONSULTATION', 'AVOCAT')")
    public ResponseEntity<byte[]> activiteExport(@RequestParam LocalDate dateDebut,
                                                  @RequestParam LocalDate dateFin,
                                                  @RequestParam(required = false) Long utilisateurId,
                                                  @RequestParam(required = false) Long avocatId,
                                                  @RequestParam FormatRapport format) {
        RapportActiviteResponse rapport =
                rapportActiviteService.produire(utilisateurId, avocatId, dateDebut, dateFin);
        if (rapport.estVide()) {
            return ResponseEntity.noContent().build();
        }
        return switch (format) {
            case JSON -> throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Le format JSON se lit sur /rapports/activite, non sur son export.");
            case PDF -> fichier(rapportActiviteExportService.exportPdf(rapport),
                    rapportActiviteExportService.nomFichier(rapport, "pdf"), MediaType.APPLICATION_PDF);
            case EXCEL -> fichier(rapportActiviteExportService.exportExcel(rapport),
                    rapportActiviteExportService.nomFichier(rapport, "xlsx"),
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            case CSV -> fichier(rapportActiviteExportService.exportCsv(rapport),
                    rapportActiviteExportService.nomFichier(rapport, "csv"),
                    MediaType.parseMediaType("text/csv"));
        };
    }

    /**
     * Actions behind a count of the activity sheet, each with its case id and reference so the UI
     * can open the case. Same access scope as the sheet, enforced by the same service.
     */
    @GetMapping("${api.prefix}/rapports/activite/details")
    @PreAuthorize("hasAnyRole('CONSULTATION', 'AVOCAT')")
    public PageResponse<ActionDetailleeResponse> activiteDetails(@RequestParam LocalDate dateDebut,
                                                                  @RequestParam LocalDate dateFin,
                                                                  @RequestParam TypeActionHistorique code,
                                                                  @RequestParam(required = false) Long utilisateurId,
                                                                  @RequestParam(required = false) Long avocatId,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(rapportActiviteService.detailler(
                utilisateurId, avocatId, code, dateDebut, dateFin, PageRequest.of(page, size)));
    }

    /** Download response, with the same headers for all three formats. */
    private static ResponseEntity<byte[]> fichier(byte[] contenu, String nom, MediaType type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"")
                .contentType(type)
                .body(contenu);
    }

    /**
     * Requester's name for the footer. Never blocking: without it, the document is still produced
     * and the footer shows only the date.
     */
    private String nomDemandeur() {
        try {
            var utilisateur = currentUserService.getCurrentUser();
            return utilisateur == null ? null : utilisateur.getPrenom() + " " + utilisateur.getNom();
        } catch (RuntimeException ignore) {
            return null;
        }
    }
}
