package com.afriland.audiences.intervenant.web.dto;

/**
 * A lawyer from the directory, with what helps decide whether to assign them a case (RG-INT-01).
 *
 * @param indicateurCharge      number of active cases assigned
 * @param deliberesFavorables   deliberations in the bank's favour on their cases
 * @param deliberesTotal        all deliberations rendered; zero means "no measure", not poor
 *                              performance
 * @param indicateurPerformance corresponding rate between 0 and 1. No longer displayed (the counts
 *                              above are shown instead) but kept in the contract for backward
 *                              compatibility.
 */
public record AvocatRepertoireResponse(
        Long id,
        String nom,
        String email,
        String telephone,
        long indicateurCharge,
        long deliberesFavorables,
        long deliberesTotal,
        double indicateurPerformance) {
}
