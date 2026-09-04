package com.afriland.audiences.dossier.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record DemandeDerogationSeuilRequest(
        @NotNull @PositiveOrZero Double nouveauSeuil,
        @NotBlank @Size(max = 255) String motif) {
}
