package com.afriland.audiences.decision.web.dto;

import java.util.List;

/**
 * M16 (Q-88) : décisions devenues définitives d'un dossier, chaque liste de la plus récente à la
 * plus ancienne — onglet « Décisions définitives » de la fiche, et seule voie pour retrouver
 * l'identifiant d'une condamnation dont on met le statut de paiement à jour (#53).
 */
public record DecisionsDossierResponse(List<AdjudicationResponse> adjudications, List<CondamnationResponse> condamnations) {
}
