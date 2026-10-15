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
 * Le référentiel des agences (métier, 2026-10-03, observation 2).
 *
 * <p><b>Lu par tous, tenu par la direction.</b> Tout porteur de la consultation interroge la liste
 * — elle alimente la liste déroulante du formulaire de création. Seuls le DJ et la DJA la
 * modifient, comme pour les paramètres système : un code d'agence engage les dossiers qui le
 * portent, et sa tenue n'est pas un geste de saisie.
 *
 * <p><b>Pas de suppression.</b> {@code DELETE} désactive, et
 * {@code PATCH /{id}/reactivation} rouvre. Un dossier ouvert sous une agence fermée reste un
 * dossier valide, et son code doit continuer de désigner quelque chose — l'audit pose des questions
 * des années après. La fermeture est donc réversible : une erreur de manipulation se corrige, et
 * une agence qui réouvre garde son code.
 */
@RestController
@RequestMapping("${api.prefix}/referentiels/agences")
@RequiredArgsConstructor
public class AgenceController {

    private final AgenceService agenceService;

    /**
     * @param recherche       fragment cherché dans le code ou le nom, insensible à la casse
     * @param actifSeulement  vrai par défaut : la saisie ne propose que les agences ouvertes.
     *                        L'écran d'administration passe {@code false} pour tout voir.
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

    /** Désactive : l'agence quitte les listes de choix, les dossiers qui la citent restent intacts. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public AgenceResponse desactiver(@PathVariable Long id) {
        return agenceService.desactiver(id);
    }

    /**
     * Réouvre une agence fermée : elle revient dans les listes de choix, avec son code d'origine.
     *
     * <p>{@code PATCH} et non {@code PUT} : on ne remplace pas l'agence, on change son seul état
     * d'ouverture - même forme que {@code PATCH /audiences/{id}/annulation}. Une agence déjà
     * ouverte est rendue telle quelle, sans refus : l'état demandé est atteint.
     */
    @PatchMapping("/{id}/reactivation")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public AgenceResponse reactiver(@PathVariable Long id) {
        return agenceService.reactiver(id);
    }
}
