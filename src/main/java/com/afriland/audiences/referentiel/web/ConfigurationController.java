package com.afriland.audiences.referentiel.web;

import com.afriland.audiences.referentiel.service.ConfigurationService;
import com.afriland.audiences.referentiel.web.dto.ConfigurationResponse;
import com.afriland.audiences.referentiel.web.dto.ModifierConfigurationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** CONF01-07 (US §11.2) — cf. API_IMPLEMENTATION_STATUS.md §9 (#47, #48). */
@RestController
@RequestMapping("${api.prefix}/configurations")
@RequiredArgsConstructor
public class ConfigurationController {

    private final ConfigurationService configurationService;

    @GetMapping
    @PreAuthorize("hasRole('CONSULTATION')")
    public List<ConfigurationResponse> lister() {
        return configurationService.lister();
    }

    /**
     * Écriture des paramètres système — <strong>DJ et DJA</strong> (Q-108).
     *
     * <p>Ouverte à la DJA sur décision du porteur du projet. Le motif est la <strong>continuité de
     * service</strong> : le DJ absent, plus personne ne pouvait relever un seuil de prorogation ou la
     * taille maximale d'un document, alors qu'un Adjoint existe précisément pour suppléer. La
     * traçabilité n'y perd rien — {@link ConfigurationService#modifier} historise chaque écriture
     * avec son auteur.
     *
     * <p><strong>L'ouverture reste à sens unique</strong> : le contrôle d'opportunité des frais
     * demeure réservé à la DJA ({@code hasRole('DJA')}), sans quoi RG-INT-03 perdrait la séparation
     * de ses deux étapes.
     */
    @PutMapping("/{cle}")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public ConfigurationResponse modifier(@PathVariable String cle,
                                           @Valid @RequestBody ModifierConfigurationRequest request) {
        return configurationService.modifier(cle, request.valeur());
    }
}
