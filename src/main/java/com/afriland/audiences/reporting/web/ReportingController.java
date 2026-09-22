package com.afriland.audiences.reporting.web;

import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.reporting.domain.TypeRapport;
import com.afriland.audiences.reporting.service.RapportExportService;
import com.afriland.audiences.reporting.service.ReportingService;
import com.afriland.audiences.reporting.web.dto.FormatRapport;
import com.afriland.audiences.reporting.web.dto.RapportResponse;
import com.afriland.audiences.reporting.web.dto.TableauDeBordResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

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
