package com.afriland.audiences.intervenant.web;

import com.afriland.audiences.intervenant.service.ConstitutionService;
import com.afriland.audiences.intervenant.web.dto.CreerConstitutionRequest;
import com.afriland.audiences.intervenant.web.dto.DemandeConstitutionResponse;
import com.afriland.audiences.intervenant.web.dto.LettreConstitutionResponse;
import com.afriland.audiences.intervenant.web.dto.RedigerLettreRequest;
import com.afriland.audiences.intervenant.web.dto.ValiderConstitutionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider appointments (FR-INT-06/07, US 5.6/5.7), implemented from the user stories' acceptance
 * criteria.
 */
@RestController
@RequiredArgsConstructor
public class ConstitutionController {

    private final ConstitutionService constitutionService;

    /**
     * Requests a provider. Open to the assistant as to a legal officer: the resulting letter belongs
     * to its requester, who alone drafts it; the line manager may correct it before signing.
     */
    @PostMapping("${api.prefix}/constitutions/prestataires")
    @PreAuthorize("hasAnyRole('JURISTE', 'ASSISTANTE')")
    public ResponseEntity<DemandeConstitutionResponse> creerDemande(@Valid @RequestBody CreerConstitutionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(constitutionService.creerDemande(request));
    }

    /** Line manager's "appointments to sign" work queue. */
    @org.springframework.web.bind.annotation.GetMapping("${api.prefix}/constitutions/prestataires")
    @PreAuthorize("hasAnyRole('SH', 'JURISTE', 'ASSISTANTE')")
    public com.afriland.audiences.common.web.PageResponse<DemandeConstitutionResponse> lister(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            com.afriland.audiences.intervenant.domain.StatutConstitution statut,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        return constitutionService.lister(statut, org.springframework.data.domain.PageRequest.of(page, size));
    }

    @PutMapping("${api.prefix}/constitutions/prestataires/{id}/validation")
    @PreAuthorize("hasRole('SH')")
    public DemandeConstitutionResponse valider(@PathVariable Long id, @Valid @RequestBody ValiderConstitutionRequest request) {
        return constitutionService.valider(id, request);
    }

    /**
     * The letter of a request, draft or signed. Readable by the line manager (who must see what
     * they sign) and by the drafter.
     */
    @GetMapping("${api.prefix}/constitutions/prestataires/{id}/lettre")
    @PreAuthorize("hasAnyRole('SH', 'JURISTE', 'ASSISTANTE')")
    public LettreConstitutionResponse lettre(@PathVariable Long id) {
        return constitutionService.lettre(id);
    }

    /**
     * The signed letter laid out for the bank's pre-printed paper (no logo, side column or legal
     * notices). Addressed by document id rather than request id, since users print it from the
     * case's documents tab.
     */
    @GetMapping("${api.prefix}/constitutions/lettres/par-document/{documentId}")
    @PreAuthorize("hasAnyRole('JURISTE', 'SH', 'DJ', 'DJA')")
    public ResponseEntity<byte[]> lettrePourPapierPreimprime(@PathVariable Long documentId) {
        byte[] pdf = constitutionService.lettrePourPapierPreimprime(documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"lettre-constitution-sans-entete.pdf\"")
                .body(pdf);
    }

    /**
     * Drafts the letter. Open to the drafter and the line manager, who may correct before signing;
     * each version records its author. A signed letter is frozen (409 for everyone).
     *
     * <p>{@code @PreAuthorize} only gates the function: the service checks that the caller is this
     * letter's requester (or the line manager) and returns 403 otherwise. A role-based grant would
     * extend to DJ/DJA, since {@code ROLE_JURISTE} is composite.
     */
    @PutMapping("${api.prefix}/constitutions/prestataires/{id}/lettre")
    @PreAuthorize("hasAnyRole('JURISTE', 'SH', 'ASSISTANTE')")
    public LettreConstitutionResponse redigerLettre(@PathVariable Long id,
                                                    @Valid @RequestBody RedigerLettreRequest request) {
        return constitutionService.redigerLettre(id, request);
    }
}
