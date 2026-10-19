package com.afriland.audiences.referentiel.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Creation and update of a branch by the DJ or DJA. */
public record AgenceRequest(
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 150) String nom,
        @NotBlank @Size(max = 100) String ville,
        @Size(max = 255) String adresse,
        @Size(max = 50) String telephone,
        /** Null on creation means "active". */
        Boolean actif) {
}
