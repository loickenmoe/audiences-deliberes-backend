package com.afriland.audiences.alerte.web;

import com.afriland.audiences.alerte.domain.StatutAlerte;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.web.dto.AlerteResponse;
import com.afriland.audiences.alerte.web.dto.ModifierSeuilRequest;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.referentiel.service.ConfigurationService;
import com.afriland.audiences.referentiel.web.dto.ConfigurationResponse;
import com.afriland.audiences.alerte.service.AlerteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Module Alertes (FR-ALR-01/02, RG-ALR-01/02/03) — cf. FUNCTIONAL_MAP.md §7,
 * API_IMPLEMENTATION_STATUS.md §9. {@code PUT /alertes/seuils} ne pilote que le seuil
 * {@code CHARGE_MAX_AVOCAT} en M5 (cf. {@link ModifierSeuilRequest}).
 */
@RestController
@RequestMapping("${api.prefix}/alertes")
@RequiredArgsConstructor
public class AlerteController {

    private static final String CLE_CHARGE_MAX_AVOCAT = "CHARGE_MAX_AVOCAT";

    private final AlerteService alerteService;
    private final ConfigurationService configurationService;

    @PutMapping("/seuils")
    @PreAuthorize("hasRole('DJ')")
    public ConfigurationResponse modifierSeuil(@Valid @RequestBody ModifierSeuilRequest request) {
        if (request.type() != TypeAlerte.CHARGE_MAX_AVOCAT) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Seul le seuil du type CHARGE_MAX_AVOCAT est configurable via cet endpoint (RG-ALR-02)");
        }
        return configurationService.modifier(CLE_CHARGE_MAX_AVOCAT, String.valueOf(request.seuil()));
    }

    @GetMapping("/mes-notifications")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<AlerteResponse> mesNotifications(
            @RequestParam(required = false) StatutAlerte statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return alerteService.mesNotifications(statut, PageRequest.of(page, size));
    }

    @PatchMapping("/{id}/traiter")
    @PreAuthorize("isAuthenticated()")
    public AlerteResponse traiter(@PathVariable Long id) {
        return alerteService.traiter(id);
    }
}
