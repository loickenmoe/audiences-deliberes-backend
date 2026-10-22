package com.afriland.audiences.referentiel.web.dto;

/**
 * A branch, as read by the UI.
 *
 * <p>{@code ville} and {@code adresse} are not decorative: two branches can share a name ("MBALLA II"
 * exists under codes {@code 00108} and {@code 108}) and they let the legal officer pick the right one.
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
