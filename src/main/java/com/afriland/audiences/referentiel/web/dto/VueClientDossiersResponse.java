package com.afriland.audiences.referentiel.web.dto;

import com.afriland.audiences.dossier.web.dto.DossierResponse;

import java.util.List;

/** FR-REP-03, RG-REP-04: consolidated state of a client's procedures, by category. */
public record VueClientDossiersResponse(
        Long clientId,
        String clientNom,
        List<DossierResponse> recouvrement,
        List<DossierResponse> exploitationLitiges) {
}
