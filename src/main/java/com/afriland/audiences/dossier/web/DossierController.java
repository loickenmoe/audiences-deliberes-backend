package com.afriland.audiences.dossier.web;

import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.dossier.service.DossierService;
import com.afriland.audiences.dossier.web.dto.AuditSeuilResponse;
import com.afriland.audiences.dossier.web.dto.CreerDossierRequest;
import com.afriland.audiences.dossier.web.dto.CreerEtapeRequest;
import com.afriland.audiences.dossier.web.dto.DemandeDerogationSeuilRequest;
import com.afriland.audiences.dossier.web.dto.DossierResponse;
import com.afriland.audiences.dossier.web.dto.EtapeProcedureResponse;
import com.afriland.audiences.dossier.web.dto.ModifierAffectationRequest;
import com.afriland.audiences.dossier.web.dto.ModifierStatutEtapeRequest;
import com.afriland.audiences.dossier.web.dto.ValiderDerogationRequest;
import com.afriland.audiences.dossier.web.dto.ValiderSensibiliteRequest;
import com.afriland.audiences.referentiel.domain.CategorieDossier;
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
 * Module Dossiers (FR-DOS-01..04, RG-DOS-09) — cf. FUNCTIONAL_MAP.md §1, API_IMPLEMENTATION_STATUS.md §1.
 * {@code POST /dossiers/{id}/etapes} est un endpoint 🆕 (cf. CreerEtapeRequest) nécessaire au
 * positionnement direct d'une étape (UC-DOS-04 alt. 3-a/3-b), absent du contrat d'API formel.
 */
@RestController
@RequestMapping("${api.prefix}/dossiers")
@RequiredArgsConstructor
public class DossierController {

    private final DossierService dossierService;

    @PostMapping
    @PreAuthorize("hasRole('SAISIE')")
    public ResponseEntity<DossierResponse> creerDossier(@Valid @RequestBody CreerDossierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dossierService.creerDossier(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('CONSULTATION')")
    public PageResponse<DossierResponse> listerDossiers(
            @RequestParam(required = false) String nature,
            @RequestParam(required = false) CategorieDossier categorie,
            @RequestParam(required = false) String juridiction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return dossierService.listerDossiers(nature, categorie, juridiction, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CONSULTATION')")
    public DossierResponse consulterDossier(@PathVariable Long id) {
        return dossierService.consulterDossier(id);
    }

    @PutMapping("/{id}/affectation")
    @PreAuthorize("hasAnyRole('JURISTE', 'DJ')")
    public DossierResponse modifierAffectation(@PathVariable Long id,
                                                @RequestBody ModifierAffectationRequest request,
                                                @RequestParam(defaultValue = "false") boolean forcer) {
        return dossierService.modifierAffectation(id, request, forcer);
    }

    @PatchMapping("/{id}/etapes/{etapeId}/statut")
    @PreAuthorize("hasRole('JURISTE')")
    public EtapeProcedureResponse modifierStatutEtape(@PathVariable Long id, @PathVariable Long etapeId,
                                                        @Valid @RequestBody ModifierStatutEtapeRequest request) {
        return dossierService.modifierStatutEtape(id, etapeId, request);
    }

    @PostMapping("/{id}/etapes")
    @PreAuthorize("hasRole('JURISTE')")
    public ResponseEntity<EtapeProcedureResponse> creerEtape(@PathVariable Long id,
                                                              @Valid @RequestBody CreerEtapeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dossierService.creerEtape(id, request));
    }

    @PostMapping("/{id}/seuil-derogation")
    @PreAuthorize("hasRole('SAISIE')")
    public ResponseEntity<AuditSeuilResponse> demanderDerogationSeuil(@PathVariable Long id,
                                                                       @Valid @RequestBody DemandeDerogationSeuilRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dossierService.demanderDerogationSeuil(id, request));
    }

    @PutMapping("/{id}/seuil-derogation/{auditId}")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public AuditSeuilResponse validerDerogationSeuil(@PathVariable Long id, @PathVariable Long auditId,
                                                      @Valid @RequestBody ValiderDerogationRequest request) {
        return dossierService.validerDerogationSeuil(id, auditId, request);
    }

    @PostMapping("/{id}/sensibilite/validation")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public DossierResponse validerSensibilite(@PathVariable Long id,
                                               @Valid @RequestBody ValiderSensibiliteRequest request) {
        return dossierService.validerSensibilite(id, request);
    }
}
