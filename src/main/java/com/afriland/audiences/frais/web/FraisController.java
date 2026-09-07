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
import org.springframework.web.bind.annotation.RestController;

/**
 * Épic 4 (FR-INT-01 à 05) — cf. FUNCTIONAL_MAP.md §4, API_IMPLEMENTATION_STATUS.md §5.
 * {@code GET /frais/demandes} : endpoint 🆕, absent du contrat formel — ajouté pour la file de
 * travail de chaque profil (Q-24).
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

    @GetMapping
    @PreAuthorize("hasAnyRole('AVOCAT', 'ASSISTANTE', 'DJA', 'DJ')")
    public PageResponse<DemandeFraisResponse> lister(
            @RequestParam(required = false) StatutCircuitFrais statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return fraisService.lister(statut, PageRequest.of(page, size));
    }
}
