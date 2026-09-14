package com.afriland.audiences.portail.web;

import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.intervenant.web.dto.DemandeConstitutionResponse;
import com.afriland.audiences.portail.service.PortailAvocatService;
import com.afriland.audiences.portail.web.dto.AudienceAvocatResponse;
import com.afriland.audiences.portail.web.dto.DossierAvocatResponse;
import com.afriland.audiences.portail.web.dto.PieceJustificativeResponse;
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
 * Portail Avocat — lectures cadrées sur le prestataire connecté (Q-96, QF-01 du frontend).
 *
 * <p><b>Une surface distincte, et non l'élargissement des endpoints internes.</b> Ouvrir
 * {@code GET /dossiers} ou {@code GET /publications} à {@code ROLE_AVOCAT} aurait fait dépendre la
 * confidentialité d'un filtre à l'intérieur d'un service partagé — un oubli, et le portefeuille de la
 * banque passait à un tiers. Ces quatre lectures forment au contraire un périmètre explicite,
 * impossible à confondre avec celui du personnel interne, et leurs réponses ne portent que ce que
 * l'usage exige.
 *
 * <p>Tout est {@code hasRole('AVOCAT')} : ces endpoints n'ont aucun sens pour un profil interne, qui
 * dispose des siens.
 */
@RestController
@RequestMapping("${api.prefix}/avocat")
@RequiredArgsConstructor
public class PortailAvocatController {

    private final PortailAvocatService portailAvocatService;

    /**
     * Plus récent d'abord, comme les autres listes paginées (Q-92/Q-93). L'ordre est porté par la
     * requête du dépôt — la racine étant {@code DossierAvocat}, un {@code Sort} sur {@code createdAt}
     * s'appliquerait à l'affectation et non au dossier.
     */
    @GetMapping("/mes-dossiers")
    @PreAuthorize("hasRole('AVOCAT')")
    public PageResponse<DossierAvocatResponse> mesDossiers(@RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return portailAvocatService.mesDossiers(PageRequest.of(page, size));
    }

    @GetMapping("/mes-dossiers/{dossierId}/audiences")
    @PreAuthorize("hasRole('AVOCAT')")
    public List<AudienceAvocatResponse> audiencesDeMonDossier(@PathVariable Long dossierId) {
        return portailAvocatService.audiencesDeMonDossier(dossierId);
    }

    /**
     * UC-INT-01 étape 4 (Q-97) : dépose une pièce justificative et renvoie sa clé de stockage, à
     * reporter dans {@code piecesJustificatives} de {@code POST /frais/demandes}.
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
    public PageResponse<PublicationResponse> mesPublications(@RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return portailAvocatService.mesPublications(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateDepot", "id")));
    }

    @GetMapping("/mes-constitutions")
    @PreAuthorize("hasRole('AVOCAT')")
    public List<DemandeConstitutionResponse> mesConstitutions() {
        return portailAvocatService.mesConstitutions();
    }
}
