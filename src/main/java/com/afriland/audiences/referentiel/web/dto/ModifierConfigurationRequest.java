package com.afriland.audiences.referentiel.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * {@code @NotNull} et non {@code @NotBlank} : certaines listes acceptent la valeur vide (réglages de
 * notification). C'est la règle de la clé ({@code RegleConfiguration}) qui décide si le vide est
 * permis ; une clé sans règle reste refusée à vide par {@code ConfigurationService}.
 */
public record ModifierConfigurationRequest(@NotNull String valeur) {
}
