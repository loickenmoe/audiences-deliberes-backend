package com.afriland.audiences.reporting.web;

import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.reporting.domain.TypeRapport;
import com.afriland.audiences.reporting.service.JournalAudiencesService;
import com.afriland.audiences.reporting.service.RapportExportService;
import com.afriland.audiences.reporting.service.ReportingService;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.reporting.web.dto.AudienceDuJourResponse;
import com.afriland.audiences.reporting.web.dto.CompteRenduAttenduResponse;
import com.afriland.audiences.reporting.web.dto.FormatRapport;
import com.afriland.audiences.reporting.web.dto.RapportResponse;
import com.afriland.audiences.reporting.web.dto.TableauDeBordResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Épic 8, volet reporting (FR-REP-01/02, US 8.4/8.5) — cf. FUNCTIONAL_MAP.md §8,
 * API_IMPLEMENTATION_STATUS.md §9. US 8.6 (vue consolidée par client) est déjà exposée par
 * {@code GET /clients/{clientId}/dossiers} depuis M4 — cf. `ClientController`.
 */
@RestController
@RequiredArgsConstructor
public class ReportingController {

    private final ReportingService reportingService;
    private final RapportExportService rapportExportService;
    private final JournalAudiencesService journalAudiencesService;
    /** Pour attribuer l'export dans le pied de page : un document dit qui l'a demandé (A2). */
    private final CurrentUserService currentUserService;

    /**
     * Ouvert au DJ <b>et à la DJA</b> (Q-95). Le critère d'acceptation d'US 8.5 ne nomme que
     * {@code ROLE_DJ}, mais les spécifications fonctionnelles décrivent le tableau de bord consolidé
     * comme obtenu « à travers le DJ/DJA » (partie prenante STK-06) : la DJA instruit les mêmes
     * dossiers et valide conjointement les frais sensibles, lui refuser les indicateurs la priverait
     * du contexte de ses propres décisions. Arbitrage du porteur du projet, 2026-09-14.
     */
    @GetMapping("${api.prefix}/tableau-de-bord")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public TableauDeBordResponse tableauDeBord() {
        return reportingService.tableauDeBord();
    }

    /**
     * Le journal des audiences d'une journée (observation métier du 2026-09-25).
     *
     * <p>Ouvert aux <b>juristes</b> comme à la direction, sur décision du porteur du projet
     * (2026-09-28). D'abord réservé au DJ et à la DJA par prudence — l'écran nomme des personnes et
     * mesure ce qu'elles ont rapporté, et NF-CONF-01 reste « à définir avec la DRH » —, il s'est
     * avéré que les juristes en ont l'usage quotidien : savoir ce qui s'est dit aux audiences du
     * jour fait partie de leur métier, et le compte rendu qu'ils doivent écrire est le leur.
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
     * Les audiences passées dont le compte rendu du juriste manque encore.
     *
     * <p>La fenêtre est demandée explicitement, comme pour les rapports et la supervision : sans
     * bornes, la liste grossirait sans fin et cesserait d'être un outil de rappel. Les plus
     * anciennes viennent en tête : ce sont elles qui pressent.
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
    public ResponseEntity<?> rapports(@RequestParam TypeRapport type,
                                        @RequestParam LocalDate dateDebut,
                                        @RequestParam LocalDate dateFin,
                                        @RequestParam(defaultValue = "JSON") FormatRapport format) {
        RapportResponse rapport = reportingService.genererRapport(type, dateDebut, dateFin);
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
     * Le nom de qui demande l'export, pour le pied de page. Jamais bloquant : un document se produit
     * même si le nom manque, le pied portant alors la date d'édition.
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
