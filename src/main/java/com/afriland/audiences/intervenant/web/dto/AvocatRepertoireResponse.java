package com.afriland.audiences.intervenant.web.dto;

/**
 * {@code indicateurCharge} : nombre de dossiers actifs affectés (RG-INT-01). {@code
 * indicateurPerformance} : taux de délibérés FAVORABLE sur les dossiers affectés, entre 0 et 1 —
 * seule mesure de succès disponible avant le module Décisions définitives (M12, Q-52) ; {@code 0}
 * si l'avocat n'a encore aucun délibéré (pas de « succès » mesurable, pas une contre-performance).
 */
public record AvocatRepertoireResponse(
        Long id,
        String nom,
        String email,
        String telephone,
        long indicateurCharge,
        double indicateurPerformance) {
}
