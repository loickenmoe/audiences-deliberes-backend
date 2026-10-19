package com.afriland.audiences.publication.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What management writes to a lawyer.
 *
 * @param dossierId optional: the request concerns a conduct, not a matter. When set, it places the
 *                  exchange in context and gives the history the case file to record it on
 */
public record DemanderExplicationRequest(
        @NotNull Long avocatId,
        Long dossierId,
        @NotBlank @Size(max = 4000) String contenu) {
}
