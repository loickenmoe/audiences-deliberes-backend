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
 * Épic 5 volet constitutions (FR-INT-06/07, US 5.6/5.7) — absents de l'OpenAPI formel, implémentés
 * selon les critères Gherkin des US, cf. API_IMPLEMENTATION_STATUS.md §7.
 */
@RestController
@RequiredArgsConstructor
public class ConstitutionController {

    private final ConstitutionService constitutionService;

    @PostMapping("${api.prefix}/constitutions/prestataires")
    @PreAuthorize("hasRole('JURISTE')")
    public ResponseEntity<DemandeConstitutionResponse> creerDemande(@Valid @RequestBody CreerConstitutionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(constitutionService.creerDemande(request));
    }

    /** M15 (Q-68) : file de travail « constitutions à signer » du SH — endpoint 🆕. */
    @org.springframework.web.bind.annotation.GetMapping("${api.prefix}/constitutions/prestataires")
    @PreAuthorize("hasAnyRole('SH', 'JURISTE')")
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
     * La lettre d'une demande — brouillon ou pièce signée (Q-115 B2).
     *
     * <p>Ouverte au <b>SH comme au juriste</b> : le premier doit lire ce qu'il s'apprête à signer,
     * le second relire ce qu'il a rédigé. C'est la même lecture, et la refuser au SH reviendrait à
     * lui demander de signer sans voir.
     */
    @GetMapping("${api.prefix}/constitutions/prestataires/{id}/lettre")
    @PreAuthorize("hasAnyRole('SH', 'JURISTE')")
    public LettreConstitutionResponse lettre(@PathVariable Long id) {
        return constitutionService.lettre(id);
    }

    /**
     * La lettre signée, mise en page <b>pour le papier préimprimé</b> de la banque : sans logo,
     * sans colonne ni mentions légales, que la feuille porte déjà.
     *
     * <p>Désignée par le <b>document de la GED</b> et non par la demande : c'est depuis l'onglet
     * « Documents » du dossier que le métier la reprend pour l'imprimer.
     *
     * <p>Ouverte aux profils qui impriment ou vérifient la correspondance — le juriste qui l'a
     * rédigée, le SH qui l'a signée, la direction qui en répond.
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
     * La rédaction, réservée au <b>juriste</b> (Q-115 B2).
     *
     * <p>Le SH consulte, accepte ou rejette — il ne récrit pas. C'est ce qui préserve le sens de la
     * seconde signature : celui qui signe n'est pas celui qui a écrit, et son refus renvoie la
     * lettre à son auteur avec un motif.
     */
    @PutMapping("${api.prefix}/constitutions/prestataires/{id}/lettre")
    @PreAuthorize("hasRole('JURISTE')")
    public LettreConstitutionResponse redigerLettre(@PathVariable Long id,
                                                    @Valid @RequestBody RedigerLettreRequest request) {
        return constitutionService.redigerLettre(id, request);
    }
}
