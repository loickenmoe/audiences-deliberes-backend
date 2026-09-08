package com.afriland.audiences.audience.web;

import com.afriland.audiences.audience.domain.PeriodeCalendrier;
import com.afriland.audiences.audience.service.AudienceService;
import com.afriland.audiences.audience.service.CalendrierExportService;
import com.afriland.audiences.audience.web.dto.AudienceResponse;
import com.afriland.audiences.audience.web.dto.CalendrierEntreeResponse;
import com.afriland.audiences.audience.web.dto.EnregistrerCompteRenduRequest;
import com.afriland.audiences.audience.web.dto.FormatExport;
import com.afriland.audiences.audience.web.dto.PlanifierAudienceRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Épic 2 (FR-AUD-01/02/05) — cf. FUNCTIONAL_MAP.md §2, API_IMPLEMENTATION_STATUS.md §3.
 * Le doublon d'audience (RG-AUD-06, volet resté ouvert au jalon M4 — cf.
 * DECISIONS_AND_OPEN_QUESTIONS.md Q-12) se confirme via {@code ?forcer=true}, sur le même
 * mécanisme que {@code PUT /dossiers/{id}/affectation}.
 */
@RestController
@RequiredArgsConstructor
public class AudienceController {

    private final AudienceService audienceService;
    private final CalendrierExportService calendrierExportService;

    @PostMapping("${api.prefix}/dossiers/{id}/audiences")
    @PreAuthorize("hasRole('JURISTE')")
    public ResponseEntity<AudienceResponse> planifierAudience(@PathVariable Long id,
                                                                @Valid @RequestBody PlanifierAudienceRequest request,
                                                                @RequestParam(defaultValue = "false") boolean forcer) {
        return ResponseEntity.status(HttpStatus.CREATED).body(audienceService.planifierAudience(id, request, forcer));
    }

    @PutMapping("${api.prefix}/audiences/{id}/compte-rendu")
    @PreAuthorize("hasRole('JURISTE')")
    public AudienceResponse enregistrerCompteRendu(@PathVariable Long id,
                                                     @Valid @RequestBody EnregistrerCompteRenduRequest request) {
        return audienceService.enregistrerCompteRendu(id, request);
    }

    /** M15 (Q-68) : onglet « Audiences » de la fiche dossier — endpoint 🆕. */
    @GetMapping("${api.prefix}/dossiers/{id}/audiences")
    @PreAuthorize("hasRole('CONSULTATION')")
    public List<AudienceResponse> listerAudiencesDossier(@PathVariable Long id) {
        return audienceService.listerParDossier(id);
    }

    @GetMapping("${api.prefix}/audiences/calendrier")
    @PreAuthorize("hasAnyRole('JURISTE', 'DJ')")
    public ResponseEntity<?> calendrier(@RequestParam PeriodeCalendrier periode,
                                         @RequestParam LocalDate dateDebut,
                                         @RequestParam(defaultValue = "JSON") FormatExport format) {
        List<CalendrierEntreeResponse> entrees = audienceService.calendrier(periode, dateDebut);
        return switch (format) {
            case JSON -> ResponseEntity.ok(entrees);
            case PDF -> ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=calendrier.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(calendrierExportService.exportPdf(entrees));
            case EXCEL -> ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=calendrier.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(calendrierExportService.exportExcel(entrees));
        };
    }
}
