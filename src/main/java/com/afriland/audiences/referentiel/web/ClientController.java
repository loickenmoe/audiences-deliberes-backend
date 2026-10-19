package com.afriland.audiences.referentiel.web;

import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.dossier.service.DossierService;
import com.afriland.audiences.referentiel.service.ClientService;
import com.afriland.audiences.referentiel.web.dto.ClientResponse;
import com.afriland.audiences.referentiel.web.dto.CreerClientRequest;
import com.afriland.audiences.referentiel.web.dto.ModifierClientRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /clients} is not in the API contract but is required: {@code dossier.client_id} is
 * {@code NOT NULL} and no client could be created otherwise.
 */
@RestController
@RequestMapping("${api.prefix}/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final DossierService dossierService;

    /**
     * Most recent first. Without an explicit sort, the paged query had no {@code ORDER BY}: a newly
     * created client could land on another page, and a row could appear twice or disappear between
     * pages.
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

    /**
     * Updates a client's name and contact details. The reference stays fixed (see
     * {@link ModifierClientRequest}). Same guard as creation: whoever can create a client can fix a typo.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SAISIE')")
    public ClientResponse modifierClient(@PathVariable Long id, @Valid @RequestBody ModifierClientRequest request) {
        return clientService.modifierClient(id, request);
    }

    @GetMapping("/{clientId}/dossiers")
    @PreAuthorize("hasRole('CONSULTATION')")
    public VueClientDossiersResponse vueConsolidee(@PathVariable Long clientId) {
        return dossierService.vueConsolideeParClient(clientId);
    }
}
