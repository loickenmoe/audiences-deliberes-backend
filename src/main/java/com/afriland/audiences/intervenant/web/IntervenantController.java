package com.afriland.audiences.intervenant.web;

import com.afriland.audiences.intervenant.domain.TypeIntervenant;
import com.afriland.audiences.intervenant.service.IntervenantService;
import com.afriland.audiences.intervenant.web.dto.CreerIntervenantRequest;
import com.afriland.audiences.intervenant.web.dto.IntervenantResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * M15 — administration du référentiel des intervenants (endpoints 🆕, Q-68). Aucune user story ne
 * les décrit, mais sans eux aucun avocat ne peut exister dans le système : ils conditionnent
 * l'affectation d'un dossier (RG-DOS-03), les demandes de frais (RG-INT-01), les publications et
 * les constitutions. Même raisonnement que {@code POST /clients} (Q-23, M4).
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

    @GetMapping
    @PreAuthorize("hasRole('CONSULTATION')")
    public List<IntervenantResponse> lister(@RequestParam(required = false) TypeIntervenant type) {
        return intervenantService.lister(type);
    }
}
