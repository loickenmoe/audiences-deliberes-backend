package com.afriland.audiences.intervenant.web;

import com.afriland.audiences.intervenant.service.RepertoireAvocatsService;
import com.afriland.audiences.intervenant.web.dto.AvocatRepertoireResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** FR-INT-08/US 5.8 — absent de l'OpenAPI formel, implémenté selon les critères Gherkin de l'US. */
@RestController
@RequiredArgsConstructor
public class RepertoireController {

    private final RepertoireAvocatsService repertoireAvocatsService;

    @GetMapping("${api.prefix}/repertoire/avocats")
    @PreAuthorize("hasAnyRole('JURISTE', 'ASSISTANTE')")
    public List<AvocatRepertoireResponse> lister(@RequestParam(required = false) String nom) {
        return repertoireAvocatsService.lister(nom);
    }
}
