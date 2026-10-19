package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.audit.HistoriqueActionSpecifications.Perimetre;
import com.afriland.audiences.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * Audit of recorded actions, restricted to <b>DJ and DJA</b>. Covers the whole history; the
 * "outside any case" view is a perimeter filter value, not a separate endpoint.
 *
 * <p>Restricted beyond {@code ROLE_CONSULTATION} because the read is aggregated and filterable by
 * author. Only modifications are recorded (plus consultations of this screen), so a missing line
 * does not prove an action did not happen.
 */
@RestController
@RequestMapping("${api.prefix}/historique")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final AuditExportService auditExportService;
    private final ExportAuditRepository exportAuditRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public AuditResponse audit(
            @RequestParam(defaultValue = "TOUT") Perimetre perimetre,
            @RequestParam(required = false) Long dossierId,
            /* Author filter, allowed by NF-CONF-01; it is why consulting this screen is recorded. */
            @RequestParam(required = false) Long utilisateurId,
            /* Typed code: an unknown value is rejected with a 400 instead of silently matching nothing. */
            @RequestParam(required = false) TypeActionHistorique code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return auditService.consulter(perimetre, dossierId, utilisateurId, code, dateDebut, dateFin, page, size);
    }

    /**
     * Exports the audit to a sealed file, returned to the caller and archived.
     *
     * <p>The period is mandatory here (optional on screen): an archive without a period would prove
     * its integrity but never its completeness. Period shortcuts (week, month...) are computed by
     * the client, so any free period stays possible.
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public ResponseEntity<byte[]> exporter(
            @RequestParam(defaultValue = "TOUT") Perimetre perimetre,
            @RequestParam(required = false) Long dossierId,
            @RequestParam(required = false) Long utilisateurId,
            @RequestParam(required = false) TypeActionHistorique code,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(defaultValue = "PDF") FormatExportAudit format) {
        var extraction = auditExportService.exporter(
                perimetre, dossierId, utilisateurId, code, dateDebut, dateFin, format);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, format.typeMime())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + extraction.nomFichier() + "\"")
                // The hash travels with the file so the recipient can compare it without the application.
                .header("X-Empreinte-SHA256", extraction.archive().getEmpreinte())
                .body(extraction.contenu());
    }

    /** Produced archives, newest first, with their hash. */
    @GetMapping("/exports")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public PageResponse<ExportAuditResponse> archives(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return PageResponse.of(
                exportAuditRepository.findAllByOrderByDateExportDescIdDesc(PageRequest.of(page, size)),
                AuditExportService.versReponse());
    }

    /**
     * Checks whether a file matches a known archive. The SHA-256 is computed by the browser; only the
     * hash is sent, the file never leaves the user's machine.
     */
    @GetMapping("/exports/verification")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public VerificationResponse verifier(@RequestParam String empreinte) {
        return auditExportService.verifier(empreinte);
    }

    /** Returns a short-lived presigned URL for an existing archive; each download is recorded. */
    @GetMapping("/exports/{id}/telechargement")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public Map<String, String> telecharger(@PathVariable Long id) {
        return Map.of("url", auditExportService.telecharger(id));
    }
}
