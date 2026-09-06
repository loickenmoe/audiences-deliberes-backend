package com.afriland.audiences.audience.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PlanifierAudienceRequest(@NotNull Long etapeId, @NotNull LocalDate datePlanifiee) {
}
