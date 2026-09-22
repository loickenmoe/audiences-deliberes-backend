package com.afriland.audiences.intervenant.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 🆕 {@code PUT /intervenants/{id}} — sur le modèle de {@code PUT /clients/{id}} (Q-116), ouvert le
 * 2026-09-22 (Q-117).
 *
 * <p><b>Le type n'y figure pas : il est figé.</b> Avocat et autre prestataire vivent dans deux tables
 * distinctes (héritage {@code JOINED}), et les dossiers, frais, publications et constitutions pointent
 * vers l'identifiant de l'avocat : changer de type reviendrait à supprimer puis recréer l'intervenant.
 *
 * <p><b>L'identifiant Keycloak d'un avocat n'y figure pas non plus : il est figé</b> — décision du
 * porteur du projet. Les comptes sont créés et authentifiés par Keycloak ; l'application ne fait que
 * s'y rattacher. Laisser ce rattachement modifiable ici ouvrirait un second chemin, hors de Keycloak,
 * pour décider qui se connecte en tant que quel avocat. Seules les informations descriptives se
 * corrigent : nom, courriel, téléphone, et pour un autre prestataire l'indicateur de notification.
 */
public record ModifierIntervenantRequest(
        @NotBlank @Size(max = 100) String nom,
        @Email @Size(max = 255) String email,
        @Size(max = 20) String telephone,
        Boolean notification) {
}
