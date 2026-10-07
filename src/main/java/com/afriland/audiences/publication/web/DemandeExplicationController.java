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
 * La demande d'explication (N7 temps 2, observation 8).
 *
 * <p><b>Trois rôles, trois gestes.</b> Le DJ et la DJA écrivent ; l'avocat répond ; les deux lisent
 * la file, chacun la sienne. Le périmètre de lecture se décide <b>dans le service</b>, sur
 * l'identité de qui appelle : un paramètre d'écran suffirait à un avocat pour demander la liste
 * entière.
 *
 * <p>Les profils sont lus tels quels, jamais par un rôle : {@code ROLE_JURISTE} est composite
 * (Q-81) et ouvrirait l'écriture à tous les juristes. C'est la règle posée par Q-126.
 */
@RestController
@RequestMapping("${api.prefix}/demandes-explication")
@RequiredArgsConstructor
public class DemandeExplicationController {

    private final DemandeExplicationService demandeExplicationService;

    /** La direction demande à un avocat de s'expliquer. 409 si une demande attend déjà sa réponse. */
    @PostMapping
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public ResponseEntity<DemandeExplicationResponse> demander(
            @Valid @RequestBody DemanderExplicationRequest requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(demandeExplicationService.demander(requete));
    }

    /**
     * L'avocat répond, une seule fois.
     *
     * <p>{@code PUT} et non {@code POST} : on complète une demande existante, on n'en crée pas une
     * seconde. Le service refuse une réponse qui ne vous est pas adressée (403) et une seconde
     * réponse (409).
     */
    @PutMapping("/{id}/reponse")
    @PreAuthorize("hasRole('AVOCAT')")
    public DemandeExplicationResponse repondre(@PathVariable Long id,
                                               @Valid @RequestBody RepondreExplicationRequest requete) {
        return demandeExplicationService.repondre(id, requete);
    }

    /**
     * La file : celle de la direction, ou celle de l'avocat.
     *
     * <p>{@code enAttente} ne vaut que pour la direction - un avocat voit les siennes, répondues ou
     * non, et c'est l'historique de ce qu'on lui a demandé.
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
