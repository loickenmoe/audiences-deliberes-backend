package com.afriland.audiences.frais.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record DeposerFraisRequest(
        @NotNull Long dossierId,
        @NotNull @Positive Double montant,
        @NotEmpty List<String> piecesJustificatives,
        @NotNull String referenceFacture) {
}
