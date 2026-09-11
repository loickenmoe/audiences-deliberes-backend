package com.afriland.audiences.delibere.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RabattementRequest(@NotNull LocalDate date, @NotBlank String motif) {
}
