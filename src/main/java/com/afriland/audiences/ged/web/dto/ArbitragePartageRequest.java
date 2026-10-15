package com.afriland.audiences.ged.web.dto;

import com.afriland.audiences.ged.domain.DecisionPartage;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * La décision du DJ ou de la DJA sur un partage proposé (QF-64). Le motif est exigé par le service
 * sur un rejet : le juriste qui a proposé doit savoir pourquoi la pièce ne part pas.
 */
public record ArbitragePartageRequest(@NotNull DecisionPartage decision, @Size(max = 1000) String motif) {
}
