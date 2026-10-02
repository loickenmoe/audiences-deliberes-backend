package com.afriland.audiences.audience.web.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * {@code objet} facultatif : par défaut, l'objet de l'alarme précédente est repris tel quel.
 * {@code dateEcheance} au futur, comme à la création : reprogrammer, c'est repousser.
 */
public record ReprogrammerAlarmeRequest(String objet, @NotNull @Future LocalDateTime dateEcheance) {
}
