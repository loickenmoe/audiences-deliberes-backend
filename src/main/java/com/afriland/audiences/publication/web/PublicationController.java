package com.afriland.audiences.publication.web;

import com.afriland.audiences.publication.domain.StatutPublication;
import com.afriland.audiences.publication.domain.TypeAutrePublication;
import com.afriland.audiences.publication.service.PublicationService;
import com.afriland.audiences.publication.web.dto.CommentaireRequest;
import com.afriland.audiences.publication.web.dto.CommentaireResponse;
import com.afriland.audiences.publication.web.dto.CorrespondanceRequest;
import com.afriland.audiences.publication.web.dto.CorrespondanceResponse;
import com.afriland.audiences.publication.web.dto.DeposerCompteRenduRequest;
import com.afriland.audiences.publication.web.dto.PublicationResponse;
import com.afriland.audiences.publication.web.dto.ValiderPublicationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Epic 5, publications (FR-INT-09). {@code PUT /publications/{id}/validation}: the contract only
 * defines {@code /publications/cr-audience/{id}/validation}, but RG-INT-06/10 apply to every
 * {@code publication} and no second endpoint exists to validate an other publication (US 5.3), so
 * the path is generalised without the {@code cr-audience} segment to cover both subtypes.
 * {@code POST /publications/{id}/correspondance} is not in the formal contract.
 */
@RestController
@RequestMapping("${api.prefix}/publications")
@RequiredArgsConstructor
public class PublicationController {

    private final PublicationService publicationService;

    /**
     * US 5.1, with an <b>optional attachment</b>.
     *
     * <p>The body is multipart to carry it, like {@code /publications/autres}: metadata as parameters,
     * the single file as a part. The report text remains mandatory; a report filed without an
     * attachment behaves as before.
     *
     * <p>DTO constraints are repeated on the parameters: without them, an empty {@code contenu} would be
     * rejected at flush (500) instead of on input (400).
     */
    @PostMapping("/cr-audience")
    @PreAuthorize("hasRole('AVOCAT')")
    public ResponseEntity<PublicationResponse> deposerCompteRendu(
            @RequestParam @NotNull Long dossierId,
            @RequestParam @NotNull Long audienceId,
            @RequestParam @NotBlank String contenu,
            @RequestParam(required = false) Long restrictionAcces,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        DeposerCompteRenduRequest request =
                new DeposerCompteRenduRequest(dossierId, audienceId, contenu, restrictionAcces);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(publicationService.deposerCompteRendu(request, file));
    }

    @PostMapping("/autres")
    @PreAuthorize("hasRole('AVOCAT')")
    public ResponseEntity<PublicationResponse> deposerAutre(@RequestParam Long dossierId,
                                                              @RequestParam TypeAutrePublication typeDocument,
                                                              @RequestParam(required = false) Long restrictionAcces,
                                                              @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(publicationService.deposerAutre(dossierId, typeDocument, file, restrictionAcces));
    }

    @PutMapping("/{id}/validation")
    @PreAuthorize("hasRole('ASSISTANTE')")
    public PublicationResponse valider(@PathVariable Long id, @Valid @RequestBody ValiderPublicationRequest request) {
        return publicationService.valider(id, request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CONSULTATION')")
    public PublicationResponse obtenir(@PathVariable Long id) {
        return publicationService.obtenir(id);
    }

    /**
     * Publications queue. {@code statut=DEPOSE} feeds the Assistant's "publications to validate"
     * screen; {@code dossierId} feeds the publications tab of a case file.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ASSISTANTE', 'JURISTE', 'DJ', 'DJA', 'SH')")
    public com.afriland.audiences.common.web.PageResponse<PublicationResponse> lister(
            @RequestParam(required = false) Long dossierId,
            @RequestParam(required = false) com.afriland.audiences.publication.domain.StatutPublication statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // The validation queue is processed in arrival order; elsewhere, most recent first.
        Sort.Direction sens = statut == StatutPublication.DEPOSE ? Sort.Direction.ASC : Sort.Direction.DESC;
        return publicationService.lister(dossierId, statut, PageRequest.of(page, size, Sort.by(sens, "dateDepot", "id")));
    }

    @PostMapping("/{id}/commentaires")
    @PreAuthorize("hasRole('JURISTE')")
    public ResponseEntity<CommentaireResponse> commenter(@PathVariable Long id, @Valid @RequestBody CommentaireRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(publicationService.commenter(id, request));
    }

    @PostMapping("/{id}/correspondance")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public ResponseEntity<CorrespondanceResponse> transmettreCorrespondance(@PathVariable Long id, @Valid @RequestBody CorrespondanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(publicationService.transmettreCorrespondance(id, request));
    }
}
