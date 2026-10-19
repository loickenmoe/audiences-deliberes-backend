package com.afriland.audiences.intervenant.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /intervenants/{id}}, modelled on {@code PUT /clients/{id}}.
 *
 * <p>The type is immutable: lawyers and other providers live in separate tables ({@code JOINED}
 * inheritance) and other entities reference the lawyer id. The Keycloak id is immutable too, so
 * that no path outside Keycloak can decide who logs in as which lawyer. Only descriptive fields
 * can be corrected.
 */
public record ModifierIntervenantRequest(
        @NotBlank @Size(max = 100) String nom,
        @Email @Size(max = 255) String email,
        @Size(max = 20) String telephone,
        Boolean notification) {
}
