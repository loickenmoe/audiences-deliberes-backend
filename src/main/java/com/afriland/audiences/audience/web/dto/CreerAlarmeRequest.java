package com.afriland.audiences.audience.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreerAlarmeRequest(
        @NotNull Long etapeId,
        @NotBlank String objet,
        @NotNull LocalDateTime dateEcheance) {
}
