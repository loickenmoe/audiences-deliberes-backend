package com.afriland.audiences.audience.web.dto;

import jakarta.validation.constraints.NotBlank;

public record EnregistrerCompteRenduRequest(@NotBlank String compteRendu) {
}
