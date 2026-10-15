package com.afriland.audiences.referentiel.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Création et modification d'une agence par le DJ ou la DJA (CONF, observation 2). */
public record AgenceRequest(
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 150) String nom,
        @NotBlank @Size(max = 100) String ville,
        @Size(max = 255) String adresse,
        @Size(max = 50) String telephone,
        /** Nul à la création vaut « active ». */
        Boolean actif) {
}
