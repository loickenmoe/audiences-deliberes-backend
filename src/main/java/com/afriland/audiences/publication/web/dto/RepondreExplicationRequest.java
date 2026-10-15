package com.afriland.audiences.publication.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ce que l'avocat répond. Une seule fois : un échange, pas une conversation. */
public record RepondreExplicationRequest(@NotBlank @Size(max = 4000) String reponse) {
}
