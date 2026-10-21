package com.afriland.audiences.intervenant.web;

import com.afriland.audiences.intervenant.service.RepertoireAvocatsService;
import com.afriland.audiences.intervenant.web.dto.AvocatRepertoireResponse;
import lombok.RequiredArgsConstructor;
import com.afriland.audiences.common.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/** Lawyer directory (FR-INT-08, US 5.8), implemented from the user story's acceptance criteria. */
@RestController
@RequiredArgsConstructor
public class RepertoireController {

    private final RepertoireAvocatsService repertoireAvocatsService;

    @GetMapping("${api.prefix}/repertoire/avocats")
    @PreAuthorize("hasAnyRole('JURISTE', 'ASSISTANTE', 'SH')")
    public PageResponse<AvocatRepertoireResponse> lister(@RequestParam(required = false) String nom,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "10") int size) {
        return repertoireAvocatsService.lister(nom, PageRequest.of(page, size));
    }
}
