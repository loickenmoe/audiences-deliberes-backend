package com.afriland.audiences.intervenant.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * What a drafter saves on an appointment letter.
 *
 * <p>Few fields are mandatory on purpose: the legal department's template may be completed by hand,
 * and missing values render as dotted lines. Only the number suffix, subject and signatory are
 * required, as without them the document is no longer a bank letter.
 *
 * @param numero number imposed by the drafter; when empty, drawn from the sequence at signature.
 *               A number already in use is rejected.
 */
public record RedigerLettreRequest(
        @Positive Integer numero,
        @NotBlank @Size(max = 160) String suffixeNumero,
        @NotBlank @Size(max = 80) String lieu,
        @Size(max = 160) String destinataireNom,
        @Size(max = 120) String destinataireQualite,
        @Size(max = 80) String destinataireVille,
        @Size(max = 255) String concerne,
        @NotBlank @Size(max = 255) String objet,
        @NotBlank String corps,
        @NotBlank @Size(max = 160) String signataire) {
}
