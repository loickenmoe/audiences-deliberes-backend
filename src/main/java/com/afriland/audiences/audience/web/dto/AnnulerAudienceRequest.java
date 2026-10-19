package com.afriland.audiences.audience.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cancellation of a scheduled hearing; the reason is mandatory. */
public record AnnulerAudienceRequest(@NotBlank @Size(max = 1000) String motif) {
}
