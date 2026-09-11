package com.afriland.audiences.delibere.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreerProrogationRequest(@NotNull LocalDate date, String motif) {
}
