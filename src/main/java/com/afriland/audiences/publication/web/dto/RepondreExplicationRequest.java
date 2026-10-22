package com.afriland.audiences.publication.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The lawyer's answer. Only once: an exchange, not a conversation. */
public record RepondreExplicationRequest(@NotBlank @Size(max = 4000) String reponse) {
}
