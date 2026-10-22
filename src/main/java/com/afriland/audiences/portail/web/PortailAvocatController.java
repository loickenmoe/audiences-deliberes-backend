package com.afriland.audiences.portail.web;

import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.intervenant.web.dto.DemandeConstitutionResponse;
import com.afriland.audiences.portail.service.PortailAvocatService;
import com.afriland.audiences.portail.web.dto.AudienceAvocatResponse;
import com.afriland.audiences.portail.web.dto.DossierAvocatResponse;
import com.afriland.audiences.portail.web.dto.PieceJustificativeResponse;
import com.afriland.audiences.portail.web.dto.PieceTelechargementResponse;
import com.afriland.audiences.portail.web.dto.PiecesDossierAvocatResponse;
import com.afriland.audiences.publication.domain.StatutPublication;
import com.afriland.audiences.publication.web.dto.PublicationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Lawyer portal: reads scoped to the logged-in provider.
 * <p>
 * A separate surface rather than opening internal endpoints to {@code ROLE_AVOCAT}: confidentiality
 * must not depend on a filter inside a shared service, where one omission would expose the bank's
 * portfolio. Responses only carry what the lawyer needs. Everything is {@code hasRole('AVOCAT')}.
 */
@RestController
@RequestMapping("${api.prefix}/avocat")
@RequiredArgsConstructor
public class PortailAvocatController {

    private final PortailAvocatService portailAvocatService;

    /**
     * Newest first, like other paged lists. Ordering is in the repository query: the root being
     * {@code DossierAvocat}, a {@code Sort} on {@code createdAt} would apply to the assignment.
     */
    @GetMapping("/mes-dossiers")
    @PreAuthorize("hasRole('AVOCAT')")
    public PageResponse<DossierAvocatResponse> mesDossiers(
            @RequestParam(required = false) String reference,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return portailAvocatService.mesDossiers(reference, PageRequest.of(page, size));
    }

    @GetMapping("/mes-dossiers/{dossierId}/audiences")
    @PreAuthorize("hasRole('AVOCAT')")
    public List<AudienceAvocatResponse> audiencesDeMonDossier(@PathVariable Long dossierId) {
        return portailAvocatService.audiencesDeMonDossier(dossierId);
    }

    /** Shared documents of the lawyer's case file, if assigned and engaged. */
    @GetMapping("/mes-dossiers/{dossierId}/pieces")
    @PreAuthorize("hasRole('AVOCAT')")
    public PiecesDossierAvocatResponse piecesDeMonDossier(@PathVariable Long dossierId) {
        return portailAvocatService.piecesDeMonDossier(dossierId);
    }

    /** Pre-signed URL of a shared document; the access is recorded. */
    @GetMapping("/mes-dossiers/{dossierId}/pieces/{documentId}")
    @PreAuthorize("hasRole('AVOCAT')")
    public PieceTelechargementResponse pieceDeMonDossier(@PathVariable Long dossierId,
                                                         @PathVariable Long documentId) {
        return portailAvocatService.pieceDeMonDossier(dossierId, documentId);
    }

    /**
     * UC-INT-01 step 4: uploads a supporting document and returns its storage key, to be passed in
     * {@code piecesJustificatives} of {@code POST /frais/demandes}.
     */
    @PostMapping("/pieces-justificatives")
    @PreAuthorize("hasRole('AVOCAT')")
    public ResponseEntity<PieceJustificativeResponse> deposerPieceJustificative(
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(portailAvocatService.deposerPieceJustificative(file));
    }

    @GetMapping("/mes-publications")
    @PreAuthorize("hasRole('AVOCAT')")
    public PageResponse<PublicationResponse> mesPublications(
            @RequestParam(required = false) StatutPublication statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return portailAvocatService.mesPublications(statut,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateDepot", "id")));
    }

    @GetMapping("/mes-constitutions")
    @PreAuthorize("hasRole('AVOCAT')")
    public List<DemandeConstitutionResponse> mesConstitutions() {
        return portailAvocatService.mesConstitutions();
    }
}
