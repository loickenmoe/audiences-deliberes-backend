package com.afriland.audiences.publication.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeposerCompteRenduRequest(
        @NotNull Long dossierId,
        @NotNull Long audienceId,
        @NotBlank String contenu,
        Long restrictionAcces) {
}
