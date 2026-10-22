package com.afriland.audiences.audience.web;

import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.audience.domain.PeriodeCalendrier;
import com.afriland.audiences.audience.service.AudienceService;
import com.afriland.audiences.audience.service.CalendrierExportService;
import com.afriland.audiences.audience.web.dto.AnnulerAudienceRequest;
import com.afriland.audiences.audience.web.dto.AudienceResponse;
import com.afriland.audiences.audience.web.dto.CalendrierEntreeResponse;
import com.afriland.audiences.audience.web.dto.EnregistrerCompteRenduRequest;
import com.afriland.audiences.audience.web.dto.NoteAvocatRequest;
import com.afriland.audiences.audience.web.dto.FormatExport;
import com.afriland.audiences.audience.web.dto.PlanifierAudienceRequest;
import com.afriland.audiences.common.web.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Hearings (FR-AUD-01/02/05). A duplicate hearing (RG-AUD-06) is confirmed with
 * {@code ?forcer=true}, same mechanism as {@code PUT /dossiers/{id}/affectation}.
 */
@RestController
@RequiredArgsConstructor
public class AudienceController {

    private final AudienceService audienceService;
    private final CalendrierExportService calendrierExportService;
    /** Used to show who requested the export in the document footer. */
    private final CurrentUserService currentUserService;

    @PostMapping("${api.prefix}/dossiers/{id}/audiences")
    @PreAuthorize("hasRole('JURISTE')")
    public ResponseEntity<AudienceResponse> planifierAudience(@PathVariable Long id,
                                                                @Valid @RequestBody PlanifierAudienceRequest request,
                                                                @RequestParam(defaultValue = "false") boolean forcer) {
        return ResponseEntity.status(HttpStatus.CREATED).body(audienceService.planifierAudience(id, request, forcer));
    }

    /**
     * Note addressed to the assigned lawyer, separate from the internal report. DJ and DJA can
     * write it too through their composite {@code ROLE_JURISTE}.
     */
    @PutMapping("${api.prefix}/audiences/{id}/note-avocat")
    @PreAuthorize("hasRole('JURISTE')")
    public AudienceResponse modifierNoteAvocat(@PathVariable Long id,
                                                @Valid @RequestBody NoteAvocatRequest request) {
        return audienceService.modifierNoteAvocat(id, request);
    }

    @PutMapping("${api.prefix}/audiences/{id}/compte-rendu")
    @PreAuthorize("hasRole('JURISTE')")
    public AudienceResponse enregistrerCompteRendu(@PathVariable Long id,
                                                     @Valid @RequestBody EnregistrerCompteRenduRequest request) {
        return audienceService.enregistrerCompteRendu(id, request);
    }

    /** Reasoned cancellation of a scheduled hearing (adjournment, wrong date). */
    @PatchMapping("${api.prefix}/audiences/{id}/annulation")
    @PreAuthorize("hasRole('JURISTE')")
    public AudienceResponse annulerAudience(@PathVariable Long id,
                                            @Valid @RequestBody AnnulerAudienceRequest request) {
        return audienceService.annulerAudience(id, request);
    }

    /** "Hearings" tab of the case page. */
    @GetMapping("${api.prefix}/dossiers/{id}/audiences")
    @PreAuthorize("hasRole('CONSULTATION')")
    public PageResponse<AudienceResponse> listerAudiencesDossier(@PathVariable Long id,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "10") int size) {
        return audienceService.listerParDossier(id, PageRequest.of(page, size));
    }

    @GetMapping("${api.prefix}/audiences/calendrier")
    @PreAuthorize("hasAnyRole('JURISTE', 'DJ', 'ASSISTANTE', 'SH')")
    public ResponseEntity<Object> calendrier(@RequestParam PeriodeCalendrier periode,
                                         @RequestParam LocalDate dateDebut,
                                         @RequestParam(defaultValue = "JSON") FormatExport format) {
        List<CalendrierEntreeResponse> entrees = audienceService.calendrier(periode, dateDebut);
        return switch (format) {
            case JSON -> ResponseEntity.ok(entrees);
            case PDF -> ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=calendrier.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(calendrierExportService.exportPdf(entrees, nomDemandeur(), null));
            case EXCEL -> ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=calendrier.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(calendrierExportService.exportExcel(entrees));
        };
    }

    /**
     * Name of the export requester, for the footer. Never blocking: if missing, the footer only
     * shows the print date.
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
