package com.afriland.audiences.dossier.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Parties to the proceedings, as sent and read by the screen. Mandatory and multiple.
 * <p>Validated here rather than in the service so the error names the faulty field, which the
 * screen shows under the right block.
 */
public record PartiesDto(
        @NotEmpty(message = "Au moins un demandeur est obligatoire")
        List<@NotBlank @Size(max = 255) String> demandeurs,

        @NotEmpty(message = "Au moins un défendeur est obligatoire")
        List<@NotBlank @Size(max = 255) String> defendeurs) {
}
