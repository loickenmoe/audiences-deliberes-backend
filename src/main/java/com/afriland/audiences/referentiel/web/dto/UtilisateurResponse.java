package com.afriland.audiences.referentiel.web.dto;

/**
 * Internal user from the reference list, used by the "assigned legal officer" selectors of the
 * case file creation and reassignment forms.
 * <p>
 * {@code keycloakId} is <b>not</b> exposed: it is an authentication identifier, useless to the
 * frontend, which only uses the application {@code id} expected by {@code juristesAffectes}
 * (see {@code CreerDossierRequest}).
 */
public record UtilisateurResponse(
        Long id,
        String nom,
        String prenom,
        String email,
        String profil,
        Boolean actif) {
}
