package com.afriland.audiences.alerte.web;

import com.afriland.audiences.alerte.domain.StatutAlerte;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.web.dto.AlerteResponse;
import com.afriland.audiences.alerte.web.dto.ModifierSeuilRequest;
import com.afriland.audiences.alerte.web.dto.StatistiquesDiffusionResponse;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.referentiel.service.ConfigurationService;
import com.afriland.audiences.referentiel.web.dto.ConfigurationResponse;
import com.afriland.audiences.alerte.service.AlerteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Alerts module (FR-ALR-01/02, RG-ALR-01/02/03). {@code PUT /alertes/seuils} only drives the
 * {@code CHARGE_MAX_AVOCAT} threshold (see {@link ModifierSeuilRequest}).
 */
@RestController
@RequestMapping("${api.prefix}/alertes")
@RequiredArgsConstructor
public class AlerteController {

    private static final String CLE_CHARGE_MAX_AVOCAT = "CHARGE_MAX_AVOCAT";

    private final AlerteService alerteService;
    private final ConfigurationService configurationService;

    /**
     * In-app channel monitoring (NF-ALR-02), DJ and DJA. NF-ALR-02 requires delivery above 99 %;
     * this endpoint makes that rate observable.
     * <p>The window is mandatory: a cumulative rate would hide a recent outage behind months of
     * normal operation.
     */
    @GetMapping("/statistiques-diffusion")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public StatistiquesDiffusionResponse statistiquesDiffusion(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return alerteService.statistiquesDiffusion(dateDebut, dateFin);
    }

    /**
     * {@code CHARGE_MAX_AVOCAT} threshold, open to <strong>DJ and DJA</strong> like
     * {@code PUT /configurations/{cle}}: the key is shown on the same screen as the other settings.
     */
    @PutMapping("/seuils")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public ConfigurationResponse modifierSeuil(@Valid @RequestBody ModifierSeuilRequest request) {
        if (request.type() != TypeAlerte.CHARGE_MAX_AVOCAT) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Seul le seuil du type CHARGE_MAX_AVOCAT est configurable via cet endpoint (RG-ALR-02)");
        }
        return configurationService.modifier(CLE_CHARGE_MAX_AVOCAT, String.valueOf(request.seuil()));
    }

    /**
     * Most recent first. {@code id} breaks ties between alerts raised in the same transaction, so
     * pagination stays stable.
     * <p>The recipient is always resolved from the token, never from a parameter.
     */
    @GetMapping("/mes-notifications")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<AlerteResponse> mesNotifications(
            @RequestParam(required = false) StatutAlerte statut,
            @RequestParam(required = false) TypeAlerte type,
            @RequestParam(required = false) Long dossierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "dateDeclenchement", "id"));
        return alerteService.mesNotifications(statut, type, dossierId, pageable);
    }

    @PatchMapping("/{id}/traiter")
    @PreAuthorize("isAuthenticated()")
    public AlerteResponse traiter(@PathVariable Long id) {
        return alerteService.traiter(id);
    }
}
