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
 * Épic 5, volet publications (FR-INT-09) — cf. FUNCTIONAL_MAP.md §5, API_IMPLEMENTATION_STATUS.md
 * §6. {@code PUT /publications/{id}/validation} : le contrat ne numérote que
 * {@code /publications/cr-audience/{id}/validation} (#30), mais RG-INT-06/10 s'appliquent
 * uniformément à la {@code publication} de base et aucun second endpoint n'est prévu pour valider
 * une « autre » publication (US 5.3) — chemin généralisé, sans le segment {@code cr-audience},
 * pour couvrir les deux sous-types (Q-55). {@code POST /publications/{id}/correspondance} :
 * « à proposer » (Q-20).
 */
@RestController
@RequestMapping("${api.prefix}/publications")
@RequiredArgsConstructor
public class PublicationController {

    private final PublicationService publicationService;

    /**
     * US 5.1, étendu par Q-98 d'une **pièce jointe facultative**.
     *
     * <p>Le corps passe en multipart pour l'accueillir, comme {@code /publications/autres} l'est
     * déjà : les métadonnées en paramètres, le seul fichier en partie. Le contrat d'API décrivait un
     * corps JSON — l'écart est assumé et consigné (Q-98), le récit restant obligatoire et le fichier
     * facultatif. Un compte rendu déposé sans pièce se comporte exactement comme avant.
     *
     * <p>Les contraintes du DTO sont reportées sur les paramètres : sans elles, un {@code contenu}
     * vide ne serait plus refusé à l'entrée mais au flush, en 500 au lieu de 400.
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
     * M15 (Q-68) : file de travail des publications — endpoint 🆕. {@code statut=DEPOSE} alimente
     * l'écran « File des publications à valider » de l'Assistante ; {@code dossierId} l'onglet
     * publications d'une fiche dossier.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ASSISTANTE', 'JURISTE', 'DJ', 'DJA')")
    public com.afriland.audiences.common.web.PageResponse<PublicationResponse> lister(
            @RequestParam(required = false) Long dossierId,
            @RequestParam(required = false) com.afriland.audiences.publication.domain.StatutPublication statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Q-86 : la file à valider se traite dans l'ordre d'arrivée ; ailleurs, la plus récente d'abord.
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
