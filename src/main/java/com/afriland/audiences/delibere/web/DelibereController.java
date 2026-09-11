package com.afriland.audiences.delibere.web;

import com.afriland.audiences.delibere.service.DelibereService;
import com.afriland.audiences.delibere.web.dto.CreerProrogationRequest;
import com.afriland.audiences.delibere.web.dto.DelibereResponse;
import com.afriland.audiences.delibere.web.dto.EnregistrerDelibereRequest;
import com.afriland.audiences.delibere.web.dto.ExpeditionRequest;
import com.afriland.audiences.delibere.web.dto.ProrogationResponse;
import com.afriland.audiences.delibere.web.dto.RabattementRequest;
import com.afriland.audiences.delibere.web.dto.RecoursResponse;
import com.afriland.audiences.delibere.web.dto.ViderDelibereRequest;
import com.afriland.audiences.dossier.web.dto.EtapeProcedureResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Épic 3 (FR-DEL-01/02/03/04/05) — cf. FUNCTIONAL_MAP.md §3, API_IMPLEMENTATION_STATUS.md §4.
 * {@code PUT /deliberes/{id}/expedition} : cité dans REF-07 §2 et les user stories (3.6) mais
 * absent de la spécification OpenAPI §3 — implémenté selon les critères Gherkin de l'US,
 * dernier volet de DECISIONS_AND_OPEN_QUESTIONS.md Q-13.
 */
@RestController
@RequiredArgsConstructor
public class DelibereController {

    private final DelibereService delibereService;

    @PostMapping("${api.prefix}/dossiers/{id}/deliberes")
    @PreAuthorize("hasRole('JURISTE')")
    public ResponseEntity<DelibereResponse> enregistrerDelibere(@PathVariable Long id,
                                                                   @Valid @RequestBody EnregistrerDelibereRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(delibereService.enregistrerDelibere(id, request));
    }

    @PostMapping("${api.prefix}/deliberes/{id}/prorogations")
    @PreAuthorize("hasRole('JURISTE')")
    public ResponseEntity<ProrogationResponse> proroger(@PathVariable Long id,
                                                          @Valid @RequestBody CreerProrogationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(delibereService.proroger(id, request));
    }

    @PostMapping("${api.prefix}/deliberes/{id}/rabattement")
    @PreAuthorize("hasRole('JURISTE')")
    public ResponseEntity<EtapeProcedureResponse> rabattre(@PathVariable Long id,
                                                             @Valid @RequestBody RabattementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(delibereService.rabattre(id, request));
    }

    /** M15 (Q-68) : onglet « Délibérés » de la fiche dossier — endpoint 🆕. */
    @GetMapping("${api.prefix}/dossiers/{id}/deliberes")
    @PreAuthorize("hasRole('CONSULTATION')")
    public java.util.List<DelibereResponse> listerDeliberesDossier(@PathVariable Long id) {
        return delibereService.listerParDossier(id);
    }

    @GetMapping("${api.prefix}/deliberes/{id}/recours")
    @PreAuthorize("hasRole('JURISTE')")
    public RecoursResponse consulterRecours(@PathVariable Long id) {
        return delibereService.consulterRecours(id);
    }

    /** Q-78 : vider le délibéré en attente — la décision est rendue, l'étape passe « Délibéré vidé ». Endpoint 🆕. */
    @PutMapping("${api.prefix}/deliberes/{id}/resultat")
    @PreAuthorize("hasRole('JURISTE')")
    public DelibereResponse viderDelibere(@PathVariable Long id, @Valid @RequestBody ViderDelibereRequest request) {
        return delibereService.viderDelibere(id, request);
    }

    /** Q-78, FR-DEL-04 critère 2 : historique des prorogations d'un délibéré — endpoint 🆕. */
    @GetMapping("${api.prefix}/deliberes/{id}/prorogations")
    @PreAuthorize("hasRole('CONSULTATION')")
    public java.util.List<ProrogationResponse> listerProrogations(@PathVariable Long id) {
        return delibereService.listerProrogations(id);
    }

    @PutMapping("${api.prefix}/deliberes/{id}/expedition")
    @PreAuthorize("hasRole('JURISTE')")
    public DelibereResponse modifierExpedition(@PathVariable Long id, @Valid @RequestBody ExpeditionRequest request) {
        return delibereService.modifierExpedition(id, request);
    }
}
