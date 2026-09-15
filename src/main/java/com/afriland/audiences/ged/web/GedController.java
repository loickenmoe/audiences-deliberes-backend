package com.afriland.audiences.ged.web;

import com.afriland.audiences.ged.service.GedService;
import com.afriland.audiences.ged.web.dto.ApprobationSuppressionRequest;
import com.afriland.audiences.ged.web.dto.DemandeSuppressionResponse;
import com.afriland.audiences.ged.web.dto.DocumentResponse;
import com.afriland.audiences.ged.web.dto.RapportJournalierResponse;
import com.afriland.audiences.ged.web.dto.SuppressionResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.afriland.audiences.common.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * Épic 6 (FR-GED-01/02/03/04) — cf. FUNCTIONAL_MAP.md §6, API_IMPLEMENTATION_STATUS.md §8.
 * {@code PUT /ged/documents/{id}/approbation-suppression} : « à proposer » (absent du contrat
 * formel), implémenté selon les critères Gherkin de US 6.4. {@code GET /dossiers/{id}/documents}
 * (#43) : endpoint 🆕, le contrat ne prévoyant que la consultation unitaire.
 */
@RestController
@RequiredArgsConstructor
public class GedController {

    private final GedService gedService;

    @PostMapping("${api.prefix}/ged/documents")
    @PreAuthorize("hasRole('SAISIE')")
    public ResponseEntity<DocumentResponse> uploaderDocument(@RequestParam Long dossierId,
                                                                @RequestParam String typeDocument,
                                                                @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gedService.uploaderDocument(dossierId, typeDocument, file));
    }

    @GetMapping("${api.prefix}/ged/documents/{id}")
    @PreAuthorize("hasRole('CONSULTATION')")
    public DocumentResponse obtenirDocument(@PathVariable Long id) {
        return gedService.obtenirDocument(id);
    }

    @GetMapping("${api.prefix}/dossiers/{id}/documents")
    @PreAuthorize("hasRole('CONSULTATION')")
    public PageResponse<DocumentResponse> listerDocumentsDossier(@PathVariable Long id,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "10") int size) {
        return gedService.listerDocumentsDossier(id, PageRequest.of(page, size));
    }

    @DeleteMapping("${api.prefix}/ged/documents/{id}")
    @PreAuthorize("hasAnyRole('JURISTE', 'DJ', 'DJA')")
    public ResponseEntity<?> supprimerOuDemander(@PathVariable Long id) {
        SuppressionResult resultat = gedService.supprimerOuDemander(id);
        return resultat.immediate()
                ? ResponseEntity.ok(resultat.document())
                : ResponseEntity.status(HttpStatus.ACCEPTED).body(resultat.demande());
    }

    /** M15 (Q-68) : file de travail des demandes de suppression (DJ/DJA) — endpoint 🆕. */
    @GetMapping("${api.prefix}/ged/demandes-suppression")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public PageResponse<DemandeSuppressionResponse> listerDemandesSuppression(
            @RequestParam(required = false) com.afriland.audiences.ged.domain.StatutSuppression statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return gedService.listerDemandesSuppression(statut, PageRequest.of(page, size));
    }

    @PutMapping("${api.prefix}/ged/documents/{id}/approbation-suppression")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public DemandeSuppressionResponse approuverOuRejeter(@PathVariable Long id,
                                                           @Valid @RequestBody ApprobationSuppressionRequest request) {
        return gedService.approuverOuRejeter(id, request);
    }

    /**
     * Q-80 : « tout juriste » (FR-GED-04) — le DJ et la DJA ont tous les droits d'un juriste (US,
     * tableau des profils), et le document d'analyse attribue ce rapport à l'Assistante. Or ces trois
     * profils ne portent pas {@code ROLE_JURISTE} dans le realm : ils sont nommés ici.
     */
    @GetMapping("${api.prefix}/ged/rapport-journalier")
    @PreAuthorize("hasAnyRole('JURISTE', 'DJ', 'DJA', 'ASSISTANTE')")
    public RapportJournalierResponse rapportJournalier(@RequestParam(required = false) LocalDate date) {
        return gedService.rapportJournalier(date != null ? date : LocalDate.now());
    }
}
