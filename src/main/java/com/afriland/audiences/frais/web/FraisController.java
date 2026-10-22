package com.afriland.audiences.frais.web;

import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.frais.domain.StatutCircuitFrais;
import com.afriland.audiences.frais.service.FraisService;
import com.afriland.audiences.frais.web.dto.ControleConformiteRequest;
import com.afriland.audiences.frais.web.dto.ControleOpportuniteRequest;
import com.afriland.audiences.frais.web.dto.DemandeFraisResponse;
import com.afriland.audiences.frais.web.dto.DeposerFraisRequest;
import com.afriland.audiences.frais.web.dto.ValidationConjointeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.RestController;

/**
 * Epic 4 (FR-INT-01 to 05). {@code GET /frais/demandes} (work queue of each profile) and
 * {@code GET /frais/demandes/{id}} (detail) are not part of the formal contract.
 */
@RestController
@RequestMapping("${api.prefix}/frais/demandes")
@RequiredArgsConstructor
public class FraisController {

    private final FraisService fraisService;

    @PostMapping
    @PreAuthorize("hasRole('AVOCAT')")
    public ResponseEntity<DemandeFraisResponse> deposer(@Valid @RequestBody DeposerFraisRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fraisService.deposer(request));
    }

    @PutMapping("/{id}/conformite")
    @PreAuthorize("hasRole('ASSISTANTE')")
    public DemandeFraisResponse controlerConformite(@PathVariable Long id, @Valid @RequestBody ControleConformiteRequest request) {
        return fraisService.controlerConformite(id, request);
    }

    @PutMapping("/{id}/opportunite")
    @PreAuthorize("hasRole('DJA')")
    public DemandeFraisResponse controlerOpportunite(@PathVariable Long id, @Valid @RequestBody ControleOpportuniteRequest request) {
        return fraisService.controlerOpportunite(id, request);
    }

    @PostMapping("/{id}/validation-conjointe")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public ResponseEntity<DemandeFraisResponse> validerConjointement(@PathVariable Long id, @Valid @RequestBody ValidationConjointeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fraisService.validerConjointement(id, request));
    }

    @PatchMapping("/{id}/paiement")
    @PreAuthorize("hasRole('ASSISTANTE')")
    public DemandeFraisResponse enregistrerPaiement(@PathVariable Long id) {
        return fraisService.enregistrerPaiement(id);
    }

    /**
     * Most recent first; the id breaks ties between two filings in the same second.
     *
     * <p>{@code avocatId} has no effect for a lawyer, who stays restricted to their own requests: the
     * access restriction is not a filter.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('AVOCAT', 'ASSISTANTE', 'DJA', 'DJ')")
    public PageResponse<DemandeFraisResponse> lister(
            @RequestParam(required = false) StatutCircuitFrais statut,
            @RequestParam(required = false) Long dossierId,
            @RequestParam(required = false) Long avocatId,
            @RequestParam(required = false) String referenceFacture,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return fraisService.lister(statut, dossierId, avocatId, referenceFacture, dateDebut, dateFin,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    /** Detail of a request with its workflow decisions; a lawyer can only read their own. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AVOCAT', 'ASSISTANTE', 'DJA', 'DJ')")
    public DemandeFraisResponse consulter(@PathVariable Long id) {
        return fraisService.consulter(id);
    }
}
