package com.afriland.audiences.dossier.web.dto;

import com.afriland.audiences.dossier.domain.SeuilOrigine;
import com.afriland.audiences.dossier.domain.StatutValidation;
import com.afriland.audiences.referentiel.domain.CategorieDossier;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Réponse conforme au schéma {@code Dossier} du contrat d'API (REF-07), complétée par les
 * champs {@code historique}/{@code documents} attendus par US 1.3 pour la consultation détaillée
 * (extension additive, non documentée dans le schéma OpenAPI formel — {@code documents} reste
 * vide tant que le module GED n'existe pas, M8). {@code historique} n'est peuplé que pour
 * {@code GET /dossiers/{id}}, vide pour {@code GET /dossiers} (évite le N+1 sur la liste).
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
        List<EtapeProcedureResponse> etapes,
        List<Long> juristesAffectes,
        List<Long> avocatsAffectes,
        List<HistoriqueActionResponse> historique,
        List<Object> documents,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
