package com.afriland.audiences.referentiel.web.dto;

import com.afriland.audiences.dossier.web.dto.DossierResponse;

import java.util.List;

/** FR-REP-03, RG-REP-04 : état consolidé des procédures d'un client, par catégorie. */
public record VueClientDossiersResponse(
        Long clientId,
        String clientNom,
        List<DossierResponse> recouvrement,
        List<DossierResponse> exploitationLitiges) {
}
