package com.afriland.audiences.audience.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * @param juridiction juridiction devant laquelle l'audience se tiendra. Facultative : laissée vide,
 *                    elle reprend celle du dossier — le cas courant du premier degré. Elle se
 *                    précise pour un appel ou un pourvoi, qui se tiennent ailleurs.
 */
public record PlanifierAudienceRequest(@NotNull Long etapeId, @NotNull LocalDate datePlanifiee,
                                       @Size(max = 100) String juridiction) {
}
