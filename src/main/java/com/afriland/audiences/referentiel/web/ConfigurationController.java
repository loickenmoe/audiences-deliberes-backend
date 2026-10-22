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

/** System parameters CONF01-07 (US §11.2). */
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
     * Writing system parameters: <strong>DJ and DJA</strong>.
     *
     * <p>Open to the DJA for service continuity: when the DJ is absent, the Deputy can still adjust a
     * parameter. Traceability is preserved: {@link ConfigurationService#modifier} records each change
     * with its author.
     *
     * <p>The expense opportunity check remains reserved to the DJA ({@code hasRole('DJA')}), otherwise
     * RG-INT-03 would lose the separation of its two steps.
     */
    @PutMapping("/{cle}")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public ConfigurationResponse modifier(@PathVariable String cle,
                                           @Valid @RequestBody ModifierConfigurationRequest request) {
        return configurationService.modifier(cle, request.valeur());
    }
}
