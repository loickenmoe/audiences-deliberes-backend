package com.afriland.audiences.audience.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Q-76 : annulation d'une audience planifiée — le motif est obligatoire. */
public record AnnulerAudienceRequest(@NotBlank @Size(max = 1000) String motif) {
}
