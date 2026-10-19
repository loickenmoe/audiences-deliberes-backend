package com.afriland.audiences.intervenant.web;

import com.afriland.audiences.intervenant.domain.TypeIntervenant;
import com.afriland.audiences.intervenant.service.IntervenantService;
import com.afriland.audiences.intervenant.web.dto.CreerIntervenantRequest;
import com.afriland.audiences.intervenant.web.dto.IntervenantResponse;
import com.afriland.audiences.intervenant.web.dto.ModifierIntervenantRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.afriland.audiences.common.web.PageResponse;
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
import org.springframework.web.bind.annotation.RestController;


/**
 * Administration of the participant reference data. Without these endpoints no lawyer could exist,
 * and lawyers are required for case assignment (RG-DOS-03), fee requests (RG-INT-01),
 * publications and appointments.
 */
@RestController
@RequestMapping("${api.prefix}/intervenants")
@RequiredArgsConstructor
public class IntervenantController {

    private final IntervenantService intervenantService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public ResponseEntity<IntervenantResponse> creer(@Valid @RequestBody CreerIntervenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(intervenantService.creer(request));
    }

    /**
     * Updates a participant, with the same guard as creation. The type and a lawyer's Keycloak id
     * are immutable; name, email, phone and notification flag can be corrected.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public IntervenantResponse modifier(@PathVariable Long id, @Valid @RequestBody ModifierIntervenantRequest request) {
        return intervenantService.modifier(id, request);
    }

    /**
     * Paginated and searchable by name. Selectors query the server as the user types instead of
     * loading the whole directory, hence the small default page size.
     */
    @GetMapping
    @PreAuthorize("hasRole('CONSULTATION')")
    public PageResponse<IntervenantResponse> lister(@RequestParam(required = false) TypeIntervenant type,
                                                      @RequestParam(required = false) String nom,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return intervenantService.lister(type, nom, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "nom", "id")));
    }
}
