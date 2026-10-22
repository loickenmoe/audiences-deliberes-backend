package com.afriland.audiences.audience.web.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * {@code objet} is optional and defaults to the previous alarm's subject. {@code dateEcheance} must
 * be in the future, as on creation.
 */
public record ReprogrammerAlarmeRequest(String objet, @NotNull @Future LocalDateTime dateEcheance) {
}
