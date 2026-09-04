package com.afriland.audiences.audience.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** {@code objet} facultatif : par défaut, l'objet de l'alarme précédente est repris tel quel. */
public record ReprogrammerAlarmeRequest(String objet, @NotNull LocalDateTime dateEcheance) {
}
