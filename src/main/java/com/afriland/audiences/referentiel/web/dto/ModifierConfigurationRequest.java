package com.afriland.audiences.referentiel.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ModifierConfigurationRequest(@NotBlank String valeur) {
}
