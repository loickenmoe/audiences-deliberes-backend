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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    /**
     * Seuil {@code CHARGE_MAX_AVOCAT} — <strong>DJ et DJA</strong> (Q-108), pour la même raison de
     * continuité de service que {@code PUT /configurations/{cle}}.
     *
     * <p>Réserver cette clé au seul DJ aurait été intenable : elle vit dans
     * {@code configuration_systeme} comme les autres et s'affiche sur le <strong>même écran</strong>.
     * La DJA aurait pu enregistrer un champ et pas ses voisins, ce qui aurait exigé une permission
     * par clé pour rester compréhensible.
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
     * Plus récente d'abord (Q-92). Sans tri explicite, la requête paginée n'avait <b>aucun</b>
     * {@code ORDER BY} : l'ordre des lignes était laissé à PostgreSQL, une notification pouvait
     * donc atterrir sur n'importe quelle page — et une même alerte apparaître deux fois, ou
     * disparaître, en changeant de page. {@code id} départage les alertes déclenchées dans la même
     * transaction, qui portent l'horodatage à la milliseconde près.
     * <p>
     * {@code type} et {@code dossierId} sont arrivés en M22 (Q-102) : avec dix-sept types d'alerte
     * et des rappels d'audience à J-7, J-3 et J-1, la liste se remplit vite, et rien ne permettait
     * d'y chercher. Le destinataire, lui, reste résolu sur le jeton — il n'est pas un paramètre.
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
