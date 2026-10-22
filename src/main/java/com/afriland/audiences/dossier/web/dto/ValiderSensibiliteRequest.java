package com.afriland.audiences.dossier.web.dto;

import com.afriland.audiences.dossier.domain.StatutValidation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * UC-DOS-01 alt. 6-a: DJ/DJA can approve, reject (reason mandatory), or adjust the proposed
 * threshold / sensitive client type before validation.
 */
public record ValiderSensibiliteRequest(
        @NotNull StatutValidation statut,
        @Size(max = 255) String motifRejet,
        @PositiveOrZero Double seuilMontantAjuste,
        @Size(max = 50) String typeClientSensibleAjuste) {
}
