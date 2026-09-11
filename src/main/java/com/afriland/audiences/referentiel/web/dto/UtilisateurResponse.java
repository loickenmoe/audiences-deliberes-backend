package com.afriland.audiences.referentiel.web.dto;

/**
 * Utilisateur interne du référentiel (M15, Q-69) — alimente les sélecteurs « juriste affecté »
 * des formulaires de création et de réaffectation d'un dossier.
 * <p>
 * {@code keycloakId} n'est <b>pas</b> exposé : c'est un identifiant d'authentification, sans usage
 * pour le frontend, qui ne manipule que l'identifiant applicatif {@code id} — le seul attendu par
 * {@code juristesAffectes} (cf. {@code CreerDossierRequest}).
 */
public record UtilisateurResponse(
        Long id,
        String nom,
        String prenom,
        String email,
        String profil,
        Boolean actif) {
}
