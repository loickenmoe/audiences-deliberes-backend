package com.afriland.audiences.dossier.web;

import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.dossier.service.DossierService;
import com.afriland.audiences.dossier.web.dto.AuditSeuilResponse;
import com.afriland.audiences.dossier.web.dto.CreerDossierRequest;
import com.afriland.audiences.dossier.web.dto.CreerEtapeRequest;
import com.afriland.audiences.dossier.web.dto.DemandeDerogationSeuilRequest;
import com.afriland.audiences.dossier.web.dto.DossierResponse;
import com.afriland.audiences.dossier.web.dto.HistoriqueActionResponse;
import com.afriland.audiences.dossier.web.dto.EtapeProcedureResponse;
import com.afriland.audiences.dossier.web.dto.ModifierAffectationRequest;
import com.afriland.audiences.dossier.web.dto.ModifierSensibiliteRequest;
import com.afriland.audiences.dossier.web.dto.ProposerSensibiliteRequest;
import com.afriland.audiences.dossier.web.dto.ModifierStatutEtapeRequest;
import com.afriland.audiences.dossier.web.dto.ValiderDerogationRequest;
import com.afriland.audiences.dossier.web.dto.ValiderSensibiliteRequest;
import com.afriland.audiences.referentiel.domain.CategorieDossier;
import java.util.List;
import com.afriland.audiences.dossier.domain.StatutValidation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.domain.TypeEtape;
import com.afriland.audiences.dossier.domain.TriDossiers;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
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
 * Cases module (FR-DOS-01..04, RG-DOS-09). {@code POST /dossiers/{id}/etapes} (see
 * CreerEtapeRequest) supports direct positioning of a step (UC-DOS-04 alt. 3-a/3-b) and is not part
 * of the formal API contract.
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

    /**
     * {@code mesDossiers} is resolved from the authenticated user: no in-house lawyer id is passed,
     * so another user's portfolio cannot be read this way.
     * <p>
     * {@code sensibiliteStatut = EN_ATTENTE} gives DJ and DJA the queue of sensitivities awaiting
     * validation.
     */
    @GetMapping
    @PreAuthorize("hasRole('CONSULTATION')")
    public PageResponse<DossierResponse> listerDossiers(
            @RequestParam(required = false) String nature,
            @RequestParam(required = false) CategorieDossier categorie,
            @RequestParam(required = false) String juridiction,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) Long clientId,
            @RequestParam(defaultValue = "false") boolean mesDossiers,
            @RequestParam(required = false) Boolean estSensible,
            @RequestParam(required = false) StatutValidation sensibiliteStatut,
            @RequestParam(required = false) TypeEtape typeEtape,
            @RequestParam(required = false) StatutCycleVie statutEtape,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(defaultValue = "RECENT") TriDossiers tri,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        // Most recent first by default; other orders are those listed in `TriDossiers`.
        return dossierService.listerDossiers(nature, categorie, juridiction, reference, clientId,
                mesDossiers, estSensible, sensibiliteStatut, typeEtape, statutEtape, dateDebut, dateFin,
                PageRequest.of(page, size, tri.sort()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CONSULTATION')")
    public DossierResponse consulterDossier(@PathVariable Long id) {
        return dossierService.consulterDossier(id);
    }

    /** History tab: the case page only embeds the first page. */
    @GetMapping("/{id}/historique")
    @PreAuthorize("hasRole('CONSULTATION')")
    public PageResponse<HistoriqueActionResponse> historique(@PathVariable Long id,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "10") int size) {
        return dossierService.historique(id, PageRequest.of(page, size));
    }

    @PutMapping("/{id}/affectation")
    @PreAuthorize("hasAnyRole('JURISTE', 'DJ')")
    public DossierResponse modifierAffectation(@PathVariable Long id,
                                                @Valid @RequestBody ModifierAffectationRequest request,
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

    /**
     * Threshold requests of a case; {@code statut=EN_ATTENTE} gives the queue to arbitrate.
     * Read-only, under {@code ROLE_CONSULTATION} like the case page.
     */
    @GetMapping("/{id}/seuil-derogation")
    @PreAuthorize("hasRole('CONSULTATION')")
    public List<AuditSeuilResponse> listerDerogationsSeuil(@PathVariable Long id,
                                                            @RequestParam(required = false) StatutValidation statut) {
        return dossierService.listerDerogationsSeuil(id, statut);
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

    /**
     * The in-house lawyer proposes special diligence on an already open case. {@code ROLE_SAISIE}
     * proposes, {@code ROLE_DJ}/{@code ROLE_DJA} decide through the validation endpoint above.
     */
    @PostMapping("/{id}/sensibilite/proposition")
    @PreAuthorize("hasRole('SAISIE')")
    public ResponseEntity<DossierResponse> proposerSensibilite(@PathVariable Long id,
                                                                @Valid @RequestBody ProposerSensibiliteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dossierService.proposerSensibilite(id, request));
    }

    /**
     * DJ or DJA sets or lifts special diligence <b>directly</b>, without a proposal. {@code PUT}
     * because it sets the case's diligence state. Removal requires a reason; setting requires
     * threshold and client type.
     */
    @PutMapping("/{id}/sensibilite")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public DossierResponse modifierSensibilite(@PathVariable Long id,
                                                @Valid @RequestBody ModifierSensibiliteRequest request) {
        return dossierService.modifierSensibilite(id, request);
    }
}
