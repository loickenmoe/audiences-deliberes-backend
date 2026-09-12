package com.afriland.audiences.referentiel.web;

import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.dossier.service.DossierService;
import com.afriland.audiences.referentiel.service.ClientService;
import com.afriland.audiences.referentiel.web.dto.ClientResponse;
import com.afriland.audiences.referentiel.web.dto.CreerClientRequest;
import com.afriland.audiences.referentiel.web.dto.VueClientDossiersResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cf. API_IMPLEMENTATION_STATUS.md §2. {@code POST /clients} est un endpoint 🆕
 * (DECISIONS_AND_OPEN_QUESTIONS.md Q-23) : absent du contrat d'API, pourtant nécessaire —
 * {@code dossier.client_id} est {@code NOT NULL} et aucune création n'était sinon possible.
 */
@RestController
@RequestMapping("${api.prefix}/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final DossierService dossierService;

    /**
     * Plus récent d'abord (Q-93). Sans tri explicite, la requête paginée n'avait aucun
     * {@code ORDER BY} : au-delà de vingt clients, celui qu'on venait de créer se retrouvait en
     * page 2 — invisible pour son auteur — et une même ligne pouvait apparaître deux fois, ou
     * disparaître, d'une page à l'autre.
     */
    @GetMapping
    @PreAuthorize("hasRole('CONSULTATION')")
    public PageResponse<ClientResponse> rechercherClients(
            @RequestParam(required = false) String nom,
            @RequestParam(required = false) String reference,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return clientService.rechercherClients(nom, reference,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CONSULTATION')")
    public ClientResponse consulterClient(@PathVariable Long id) {
        return clientService.consulterClient(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('SAISIE')")
    public ResponseEntity<ClientResponse> creerClient(@Valid @RequestBody CreerClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clientService.creerClient(request));
    }

    @GetMapping("/{clientId}/dossiers")
    @PreAuthorize("hasRole('CONSULTATION')")
    public VueClientDossiersResponse vueConsolidee(@PathVariable Long clientId) {
        return dossierService.vueConsolideeParClient(clientId);
    }
}
