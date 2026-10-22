package com.afriland.audiences.dossier.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Proposal of special diligence on an already open case, which may become sensitive along the way.
 * <p><b>Same requirements as on creation</b>: threshold and client type are mandatory, as this is
 * the proposal DJ or DJA will decide on (RG-DOS-09). {@code motif} is required here, unlike on
 * creation, to state what changed.
 */
public record ProposerSensibiliteRequest(
        @NotNull @PositiveOrZero Double seuilMontant,
        @NotBlank @Size(max = 50) String typeClientSensible,
        @NotBlank @Size(max = 255) String motif) {
}
