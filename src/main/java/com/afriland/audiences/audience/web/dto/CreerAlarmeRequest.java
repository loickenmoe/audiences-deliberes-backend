package com.afriland.audiences.audience.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * {@code dateEcheance} must be in the future: an alarm is a reminder of what remains to be done.
 */
public record CreerAlarmeRequest(
        @NotNull Long etapeId,
        @NotBlank String objet,
        @NotNull @Future LocalDateTime dateEcheance) {
}
