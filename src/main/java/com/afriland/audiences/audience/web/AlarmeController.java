package com.afriland.audiences.audience.web;

import com.afriland.audiences.audience.service.AlarmeService;
import com.afriland.audiences.audience.web.dto.AlarmeResponse;
import com.afriland.audiences.audience.web.dto.CreerAlarmeRequest;
import com.afriland.audiences.audience.web.dto.ReprogrammerAlarmeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /dossiers/{id}/alarmes} et {@code PUT /alarmes/{id}/reprogrammer} : cités dans le
 * tableau récapitulatif REF-07 §2 et les user stories (2.3, 2.4) mais **absents de la
 * spécification OpenAPI §3** — implémentés selon les critères Gherkin des US, cf.
 * DECISIONS_AND_OPEN_QUESTIONS.md Q-13.
 */
@RestController
@RequiredArgsConstructor
public class AlarmeController {

    private final AlarmeService alarmeService;

    @PostMapping("${api.prefix}/dossiers/{id}/alarmes")
    @PreAuthorize("hasRole('JURISTE')")
    public ResponseEntity<AlarmeResponse> creerAlarme(@PathVariable Long id,
                                                        @Valid @RequestBody CreerAlarmeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alarmeService.creerAlarme(id, request));
    }

    /** M15 (Q-68) : alarmes d'un dossier — endpoint 🆕, aucune lecture n'existait. */
    @org.springframework.web.bind.annotation.GetMapping("${api.prefix}/dossiers/{id}/alarmes")
    @PreAuthorize("hasRole('CONSULTATION')")
    public com.afriland.audiences.common.web.PageResponse<AlarmeResponse> listerAlarmesDossier(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        return alarmeService.listerParDossier(id, org.springframework.data.domain.PageRequest.of(page, size));
    }

    @PutMapping("${api.prefix}/alarmes/{id}/reprogrammer")
    @PreAuthorize("hasRole('JURISTE')")
    public AlarmeResponse reprogrammerAlarme(@PathVariable Long id,
                                              @Valid @RequestBody ReprogrammerAlarmeRequest request) {
        return alarmeService.reprogrammerAlarme(id, request);
    }

    /** Q-77 : clore une alarme traitée, sans la remplacer — endpoint 🆕. */
    @PatchMapping("${api.prefix}/alarmes/{id}/traiter")
    @PreAuthorize("hasRole('JURISTE')")
    public AlarmeResponse traiterAlarme(@PathVariable Long id) {
        return alarmeService.traiterAlarme(id);
    }
}
