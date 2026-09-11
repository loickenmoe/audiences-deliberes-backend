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

    @PutMapping("/{cle}")
    @PreAuthorize("hasRole('DJ')")
    public ConfigurationResponse modifier(@PathVariable String cle,
                                           @Valid @RequestBody ModifierConfigurationRequest request) {
        return configurationService.modifier(cle, request.valeur());
    }
}
