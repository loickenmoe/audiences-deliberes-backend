package com.afriland.audiences.referentiel.web;

import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.referentiel.service.AgenceService;
import com.afriland.audiences.referentiel.web.dto.AgenceRequest;
import com.afriland.audiences.referentiel.web.dto.AgenceResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Branch reference list.
 *
 * <p><b>Read by all, maintained by management.</b> Anyone with consultation rights can query the
 * list, which feeds the creation form's drop-down. Only the DJ and DJA modify it: a branch code
 * affects the case files that carry it.
 *
 * <p><b>No deletion.</b> {@code DELETE} deactivates and {@code PATCH /{id}/reactivation} reopens.
 * A case file opened under a closed branch remains valid and its code must keep designating
 * something. Closing is reversible: a handling error can be fixed, and a reopened branch keeps its
 * code.
 */
@RestController
@RequestMapping("${api.prefix}/referentiels/agences")
@RequiredArgsConstructor
public class AgenceController {

    private final AgenceService agenceService;

    /**
     * @param recherche       fragment searched in the code or name, case-insensitive
     * @param actifSeulement  true by default: input only offers open branches.
     *                        The admin screen passes {@code false} to see everything.
     */
    @GetMapping
    @PreAuthorize("hasRole('CONSULTATION')")
    public PageResponse<AgenceResponse> lister(
            @RequestParam(required = false) String recherche,
            @RequestParam(defaultValue = "true") boolean actifSeulement,
            Pageable pageable) {
        return agenceService.lister(recherche, actifSeulement, pageable);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    @ResponseStatus(HttpStatus.CREATED)
    public AgenceResponse creer(@Valid @RequestBody AgenceRequest request) {
        return agenceService.creer(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public AgenceResponse modifier(@PathVariable Long id, @Valid @RequestBody AgenceRequest request) {
        return agenceService.modifier(id, request);
    }

    /** Deactivates: the branch leaves selection lists; case files citing it stay intact. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public AgenceResponse desactiver(@PathVariable Long id) {
        return agenceService.desactiver(id);
    }

    /**
     * Reopens a closed branch: it returns to selection lists with its original code.
     *
     * <p>{@code PATCH} rather than {@code PUT}: only the open state changes (same form as
     * {@code PATCH /audiences/{id}/annulation}). An already open branch is returned as is.
     */
    @PatchMapping("/{id}/reactivation")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public AgenceResponse reactiver(@PathVariable Long id) {
        return agenceService.reactiver(id);
    }
}
