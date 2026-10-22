package com.afriland.audiences.audience.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * @param juridiction court where the hearing will take place. Optional: defaults to the case's
 * court (first instance); set it for an appeal or cassation.
 */
public record PlanifierAudienceRequest(@NotNull Long etapeId, @NotNull LocalDate datePlanifiee,
                                       @Size(max = 100) String juridiction) {
}
