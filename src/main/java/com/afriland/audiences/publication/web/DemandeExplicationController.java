package com.afriland.audiences.publication.web;

import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.publication.service.DemandeExplicationService;
import com.afriland.audiences.publication.web.dto.DemandeExplicationResponse;
import com.afriland.audiences.publication.web.dto.DemanderExplicationRequest;
import com.afriland.audiences.publication.web.dto.RepondreExplicationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Requests for explanation.
 *
 * <p>The DJ and DJA write, the lawyer answers, and each reads their own queue. The read scope is
 * decided <b>in the service</b> from the caller's identity: a UI parameter would let a lawyer
 * request the whole list.
 *
 * <p>Profiles are read as is, never through a role: {@code ROLE_JURISTE} is composite and would
 * open writing to all legal officers.
 */
@RestController
@RequestMapping("${api.prefix}/demandes-explication")
@RequiredArgsConstructor
public class DemandeExplicationController {

    private final DemandeExplicationService demandeExplicationService;

    /** Management asks a lawyer to explain themselves. 409 if a request is already awaiting an answer. */
    @PostMapping
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public ResponseEntity<DemandeExplicationResponse> demander(
            @Valid @RequestBody DemanderExplicationRequest requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(demandeExplicationService.demander(requete));
    }

    /**
     * The lawyer answers, only once.
     *
     * <p>{@code PUT} rather than {@code POST}: it completes an existing request. The service rejects an
     * answer to a request not addressed to the caller (403) and a second answer (409).
     */
    @PutMapping("/{id}/reponse")
    @PreAuthorize("hasRole('AVOCAT')")
    public DemandeExplicationResponse repondre(@PathVariable Long id,
                                               @Valid @RequestBody RepondreExplicationRequest requete) {
        return demandeExplicationService.repondre(id, requete);
    }

    /**
     * The queue: management's, or the lawyer's.
     *
     * <p>{@code enAttente} only applies to management; a lawyer sees all their requests, answered or
     * not, as the history of what they were asked.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('DJ', 'DJA', 'AVOCAT')")
    public PageResponse<DemandeExplicationResponse> lister(
            @RequestParam(defaultValue = "false") boolean enAttente,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return demandeExplicationService.lister(enAttente, PageRequest.of(page, size));
    }
}
