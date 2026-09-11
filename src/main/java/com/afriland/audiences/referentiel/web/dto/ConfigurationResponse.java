package com.afriland.audiences.referentiel.web.dto;

import java.time.LocalDateTime;

public record ConfigurationResponse(
        String cle,
        String valeur,
        String description,
        String modifiablePar,
        LocalDateTime dateModification) {
}
