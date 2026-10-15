package com.afriland.audiences.ged.web;

import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.ged.service.PartageDocumentService;
import com.afriland.audiences.ged.web.dto.ArbitragePartageRequest;
import com.afriland.audiences.ged.web.dto.EtatPartageDossierResponse;
import com.afriland.audiences.ged.web.dto.PartageAvocatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le partage des pièces de la GED avec l'avocat du dossier (QF-64).
 *
 * <p>{@code @PreAuthorize} dit qui touche la fonction ; le service dit sur quel dossier, et si le
 * geste est un partage ou une proposition. Le DJ et la DJA partagent par leur {@code ROLE_JURISTE}
 * composite (Q-81) ; <b>le SH et l'Assistante aussi depuis QF-65</b> - « ils sont à la base des
 * juristes ». Le SH et l'Assistante ne portant pas {@code ROLE_JURISTE}, ils sont nommés.
 */
@RestController
@RequiredArgsConstructor
public class PartageDocumentController {

    private final PartageDocumentService partageDocumentService;

    /** Partage la pièce, ou propose de la partager sur un dossier sensible. 409 si c'est déjà fait. */
    @PutMapping("${api.prefix}/ged/documents/{id}/partage-avocat")
    @PreAuthorize("hasAnyRole('JURISTE', 'SH', 'ASSISTANTE')")
    public PartageAvocatResponse partager(@PathVariable Long id) {
        return partageDocumentService.partager(id);
    }

    /** Retire le partage, ou annule une proposition. Ouvert à qui partage : retirer réduit l'exposition. */
    @DeleteMapping("${api.prefix}/ged/documents/{id}/partage-avocat")
    @PreAuthorize("hasAnyRole('JURISTE', 'SH', 'ASSISTANTE')")
    public PartageAvocatResponse retirer(@PathVariable Long id) {
        return partageDocumentService.retirer(id);
    }

    /** Dossier sensible : le DJ ou la DJA valide ou rejette, un rejet exigeant son motif. */
    @PutMapping("${api.prefix}/ged/documents/{id}/partage-avocat/arbitrage")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public PartageAvocatResponse arbitrer(@PathVariable Long id, @Valid @RequestBody ArbitragePartageRequest request) {
        return partageDocumentService.arbitrer(id, request);
    }

    /**
     * QF-65 : un avocat affecté à ce dossier y est-il constitué ? Ouvert à qui consulte le dossier :
     * c'est ce que l'onglet Documents annonce avant tout partage.
     */
    @GetMapping("${api.prefix}/dossiers/{id}/partage-avocat")
    @PreAuthorize("hasRole('CONSULTATION')")
    public EtatPartageDossierResponse etat(@PathVariable Long id) {
        return partageDocumentService.etat(id);
    }

    /** La file des partages qui attendent le DJ ou la DJA, le plus ancien d'abord. */
    @GetMapping("${api.prefix}/ged/partages-avocat")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public PageResponse<PartageAvocatResponse> propositions(@RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return partageDocumentService.propositions(PageRequest.of(page, size));
    }
}
