package com.afriland.audiences.referentiel.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * {@code @NotNull} rather than {@code @NotBlank}: some lists accept an empty value (notification
 * settings). The key's rule ({@code RegleConfiguration}) decides whether empty is allowed; a key
 * without a rule is still rejected when empty by {@code ConfigurationService}.
 */
public record ModifierConfigurationRequest(@NotNull String valeur) {
}
