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

    /**
     * {@code reference}, {@code clientId} et {@code mesDossiers} ont été ajoutés en M16 (Q-72) :
     * sans eux, un juriste ne pouvait retrouver un dossier qu'en parcourant les pages.
     * {@code mesDossiers} se résout sur l'utilisateur authentifié — il n'y a donc pas d'identifiant
     * de juriste à passer, et pas de moyen de consulter le portefeuille d'un autre par ce biais.
     * <p>
     * {@code estSensible} et {@code sensibiliteStatut} sont arrivés en M22 (Q-101). Le second ouvre
     * au DJ et à la DJA la file des sensibilités **en attente de validation**, que rien n'exposait.
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
        // Plus récent d'abord (Q-93) : sans tri, l'ordre des lignes était laissé à PostgreSQL. Le
        // métier peut désormais en demander un autre, parmi ceux que `TriDossiers` énumère.
        return dossierService.listerDossiers(nature, categorie, juridiction, reference, clientId,
                mesDossiers, estSensible, sensibiliteStatut, typeEtape, statutEtape, dateDebut, dateFin,
                PageRequest.of(page, size, tri.sort()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CONSULTATION')")
    public DossierResponse consulterDossier(@PathVariable Long id) {
        return dossierService.consulterDossier(id);
    }

    /** Q-100 — l'onglet Historique : la fiche n'embarque que la première page. */
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
     * Q-74 — demandes de seuil d'un dossier. {@code statut=EN_ATTENTE} donne la file à arbitrer.
     * Lecture seule, sous {@code ROLE_CONSULTATION} comme la fiche dont elle dépend.
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
     * Le juriste propose la diligence particulière sur un dossier déjà ouvert (métier, 2026-10-03,
     * observation 5).
     *
     * <p>Même partage des rôles que la dérogation de seuil : {@code ROLE_SAISIE} propose,
     * {@code ROLE_DJ}/{@code ROLE_DJA} arbitrent - par l'endpoint de validation ci-dessus, qui n'a
     * pas à savoir si la proposition vient de la création ou d'ici.
     */
    @PostMapping("/{id}/sensibilite/proposition")
    @PreAuthorize("hasRole('SAISIE')")
    public ResponseEntity<DossierResponse> proposerSensibilite(@PathVariable Long id,
                                                                @Valid @RequestBody ProposerSensibiliteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dossierService.proposerSensibilite(id, request));
    }

    /**
     * Le DJ ou la DJA pose ou lève la diligence particulière <b>directement</b>, sans proposition
     * (métier, 2026-10-03, observation 5 ; les deux sens arrêtés le 2026-10-06).
     *
     * <p>{@code PUT} et non {@code POST} : on ne crée pas une demande, on fixe l'état de la
     * diligence du dossier. Le retrait exige un motif, la pose exige seuil et type de client.
     */
    @PutMapping("/{id}/sensibilite")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public DossierResponse modifierSensibilite(@PathVariable Long id,
                                                @Valid @RequestBody ModifierSensibiliteRequest request) {
        return dossierService.modifierSensibilite(id, request);
    }
}
