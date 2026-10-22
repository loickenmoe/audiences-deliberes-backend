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
 * Sharing GED documents with the case file's lawyer.
 *
 * <p>{@code @PreAuthorize} controls who reaches the feature; the service decides on which case file,
 * and whether the action is a share or a proposal. The DJ and DJA share through their composite
 * {@code ROLE_JURISTE}; the SH and the Assistant, treated as legal officers but not carrying
 * {@code ROLE_JURISTE}, are named explicitly.
 */
@RestController
@RequiredArgsConstructor
public class PartageDocumentController {

    private final PartageDocumentService partageDocumentService;

    /** Shares the document, or proposes sharing it on a sensitive case file. 409 if already done. */
    @PutMapping("${api.prefix}/ged/documents/{id}/partage-avocat")
    @PreAuthorize("hasAnyRole('JURISTE', 'SH', 'ASSISTANTE')")
    public PartageAvocatResponse partager(@PathVariable Long id) {
        return partageDocumentService.partager(id);
    }

    /** Withdraws the share or cancels a proposal. Open to anyone who can share: withdrawing reduces exposure. */
    @DeleteMapping("${api.prefix}/ged/documents/{id}/partage-avocat")
    @PreAuthorize("hasAnyRole('JURISTE', 'SH', 'ASSISTANTE')")
    public PartageAvocatResponse retirer(@PathVariable Long id) {
        return partageDocumentService.retirer(id);
    }

    /** Sensitive case file: the DJ or DJA approves or rejects; a rejection requires a reason. */
    @PutMapping("${api.prefix}/ged/documents/{id}/partage-avocat/arbitrage")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public PartageAvocatResponse arbitrer(@PathVariable Long id, @Valid @RequestBody ArbitragePartageRequest request) {
        return partageDocumentService.arbitrer(id, request);
    }

    /**
     * Is a lawyer assigned to this case file appointed on it? Open to anyone who can view the case
     * file: the Documents tab shows this before any share.
     */
    @GetMapping("${api.prefix}/dossiers/{id}/partage-avocat")
    @PreAuthorize("hasRole('CONSULTATION')")
    public EtatPartageDossierResponse etat(@PathVariable Long id) {
        return partageDocumentService.etat(id);
    }

    /** Queue of shares awaiting the DJ or DJA, oldest first. */
    @GetMapping("${api.prefix}/ged/partages-avocat")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public PageResponse<PartageAvocatResponse> propositions(@RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return partageDocumentService.propositions(PageRequest.of(page, size));
    }
}
