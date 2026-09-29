package com.afriland.audiences.audience.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** {@code dateEcheance} au futur : une alarme rappelle ce qui reste à faire (règle portée par
 * l'écran depuis l'origine, descendue ici pour qu'elle vaille aussi par l'API). */
public record CreerAlarmeRequest(
        @NotNull Long etapeId,
        @NotBlank String objet,
        @NotNull @Future LocalDateTime dateEcheance) {
}
