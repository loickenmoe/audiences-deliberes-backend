package com.afriland.audiences.dossier.web.dto;

import com.afriland.audiences.dossier.domain.SeuilOrigine;
import com.afriland.audiences.dossier.domain.StatutValidation;
import com.afriland.audiences.referentiel.domain.CategorieDossier;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Réponse conforme au schéma {@code Dossier} du contrat d'API (REF-07), complétée par les
 * champs {@code historique}/{@code documents} attendus par US 1.3 pour la consultation détaillée
 * (extension additive, non documentée dans le schéma OpenAPI formel).
 * <p>
 * {@code historique} n'est peuplé que pour {@code GET /dossiers/{id}}, vide pour
 * {@code GET /dossiers} (évite le N+1 sur la liste).
 * <p>
 * <b>{@code documents} est toujours vide, par construction</b> (Q-67, tranché en M14) : le module
 * {@code dossier} ne peut pas dépendre de {@code ged} sans inverser les couches (principe tenu
 * depuis M6 — cf. Q-49/Q-53/Q-54/Q-56). Les pièces d'un dossier sont servies par l'endpoint dédié
 * {@code GET /dossiers/{id}/documents} (#43, module {@code ged}, M8). Le champ est conservé pour
 * ne pas rompre le contrat de réponse déjà publié, mais ne doit pas être consommé.
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
