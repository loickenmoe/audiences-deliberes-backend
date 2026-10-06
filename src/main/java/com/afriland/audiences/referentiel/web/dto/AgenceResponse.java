package com.afriland.audiences.referentiel.web.dto;

/**
 * Une agence, telle que l'écran la lit.
 *
 * <p>{@code ville} et {@code adresse} ne sont pas décoratives : deux agences peuvent porter le même
 * nom — « MBALLA II » existe sous les codes {@code 00108} et {@code 108} — et ce sont elles qui
 * permettent au juriste de choisir la bonne (arbitrage du métier, 2026-10-05).
 */
public record AgenceResponse(
        Long id,
        String code,
        String nom,
        String ville,
        String adresse,
        String telephone,
        Boolean actif) {
}
