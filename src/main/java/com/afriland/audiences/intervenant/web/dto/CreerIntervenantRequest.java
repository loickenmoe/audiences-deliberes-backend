package com.afriland.audiences.intervenant.web.dto;

import com.afriland.audiences.intervenant.domain.TypeIntervenant;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Creation of a reference-data participant. {@code compteKeycloak} is mandatory for an
 * {@code AVOCAT} (RG-INT-01: the only provider type with an account) and must be absent for an
 * {@code AUTRE_PRESTATAIRE} (RG-INT-02); {@code notification} applies only to the latter.
 */
public record CreerIntervenantRequest(
        @NotNull TypeIntervenant type,
        @NotBlank @Size(max = 100) String nom,
        @Email @Size(max = 255) String email,
        @Size(max = 20) String telephone,
        @Size(max = 255) String compteKeycloak,
        Boolean notification) {
}
