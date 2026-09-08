package com.afriland.audiences.intervenant.web.dto;

import com.afriland.audiences.intervenant.domain.TypeIntervenant;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * M15 — création d'un intervenant du référentiel (Q-68). {@code compteKeycloak} est **obligatoire
 * pour un {@code AVOCAT}** (RG-INT-01 : seul type de prestataire disposant d'un compte) et doit
 * rester absent pour un {@code AUTRE_PRESTATAIRE} (RG-INT-02) ; {@code notification} est le
 * symétrique, propre au second.
 */
public record CreerIntervenantRequest(
        @NotNull TypeIntervenant type,
        @NotBlank @Size(max = 100) String nom,
        @Email @Size(max = 255) String email,
        @Size(max = 20) String telephone,
        @Size(max = 255) String compteKeycloak,
        Boolean notification) {
}
