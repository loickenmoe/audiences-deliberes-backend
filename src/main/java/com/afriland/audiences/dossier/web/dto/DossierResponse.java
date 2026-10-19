package com.afriland.audiences.dossier.web.dto;

import com.afriland.audiences.dossier.domain.SeuilOrigine;
import com.afriland.audiences.dossier.domain.StatutValidation;
import com.afriland.audiences.referentiel.domain.CategorieDossier;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response matching the {@code Dossier} schema of the API contract (REF-07), extended with
 * {@code historique}/{@code documents} for detailed view (US 1.3).
 * <p>
 * {@code historique} is only filled for {@code GET /dossiers/{id}}, empty for {@code GET /dossiers}
 * (avoids N+1).
 * <p>
 * <b>{@code documents} is always empty by design</b>: {@code dossier} cannot depend on {@code ged}.
 * Documents are served by {@code GET /dossiers/{id}/documents}. The field is kept for contract
 * compatibility and must not be consumed.
 */
public record DossierResponse(
        Long id,
        String reference,
        String nature,
        CategorieDossier categorie,
        String juridictionSaisie,
        Long clientId,
        String clientNom,
        PartiesDto parties,
        Double risqueEncouru,
        String recoursExerces,
        String motifRenvoi,
        String codeAgenceOrigine,
        String codeAgenceContentieuse,
        String dossierCreditOrigine,
        String pvTransfert,
        String incidentCompteReference,
        String elementsJustificatifs,
        String champAlarme,
        Boolean estSensible,
        Double seuilMontant,
        String typeClientSensible,
        SeuilOrigine seuilOrigine,
        StatutValidation sensibiliteStatut,
        String sensibiliteMotifRejet,
        String sensibiliteMotifRetrait,
        List<EtapeProcedureResponse> etapes,
        List<Long> juristesAffectes,
        List<Long> avocatsAffectes,
        List<HistoriqueActionResponse> historique,
        List<Object> documents,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
