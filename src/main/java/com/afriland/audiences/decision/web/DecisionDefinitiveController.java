package com.afriland.audiences.decision.web;

import com.afriland.audiences.decision.service.DecisionDefinitiveService;
import com.afriland.audiences.decision.web.dto.AdjudicationResponse;
import com.afriland.audiences.decision.web.dto.CondamnationResponse;
import com.afriland.audiences.decision.web.dto.CreerAdjudicationRequest;
import com.afriland.audiences.decision.web.dto.CreerCondamnationRequest;
import com.afriland.audiences.decision.web.dto.DecisionsDossierResponse;
import com.afriland.audiences.decision.web.dto.ModifierStatutCondamnationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Final decisions (FR-DEF-01/02, US 8.1/8.2). These endpoints are not in the formal OpenAPI contract. */
@RestController
@RequiredArgsConstructor
public class DecisionDefinitiveController {

    private final DecisionDefinitiveService decisionDefinitiveService;

    /** Final decisions of a case file. */
    @GetMapping("${api.prefix}/dossiers/{id}/decisions")
    @PreAuthorize("hasRole('CONSULTATION')")
    public DecisionsDossierResponse lister(@PathVariable Long id) {
        return decisionDefinitiveService.lister(id);
    }

    @PostMapping("${api.prefix}/dossiers/{id}/adjudications")
    @PreAuthorize("hasRole('JURISTE')")
    public ResponseEntity<AdjudicationResponse> creerAdjudication(@PathVariable Long id, @Valid @RequestBody CreerAdjudicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(decisionDefinitiveService.creerAdjudication(id, request));
    }

    @PostMapping("${api.prefix}/dossiers/{id}/condamnations")
    @PreAuthorize("hasRole('JURISTE')")
    public ResponseEntity<CondamnationResponse> creerCondamnation(@PathVariable Long id, @Valid @RequestBody CreerCondamnationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(decisionDefinitiveService.creerCondamnation(id, request));
    }

    @PatchMapping("${api.prefix}/condamnations/{id}/statut")
    @PreAuthorize("hasRole('JURISTE')")
    public CondamnationResponse modifierStatutCondamnation(@PathVariable Long id, @Valid @RequestBody ModifierStatutCondamnationRequest request) {
        return decisionDefinitiveService.modifierStatutCondamnation(id, request);
    }
}
