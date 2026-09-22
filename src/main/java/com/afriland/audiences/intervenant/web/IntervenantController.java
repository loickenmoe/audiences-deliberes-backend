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

    /**
     * 🆕 Modifie un intervenant (Q-117) — même garde que la création. Le type et l'identifiant
     * Keycloak d'un avocat sont figés ; nom, courriel, téléphone et notification se corrigent.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public IntervenantResponse modifier(@PathVariable Long id, @Valid @RequestBody ModifierIntervenantRequest request) {
        return intervenantService.modifier(id, request);
    }

    /**
     * Paginé depuis Q-100, et **cherchable par nom depuis Q-105**.
     *
     * <p>C'est ce paramètre {@code nom} qui règle le vrai problème des sélecteurs. Une liste
     * déroulante qui demandait « une grande page » cassait silencieusement dès que l'annuaire la
     * dépassait ; elle interroge désormais le serveur au fil de la frappe et n'a plus besoin de tout
     * détenir. La taille par défaut redescend donc à 20 : ce qu'affiche une liste déroulante, pas ce
     * qu'elle contient.
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
