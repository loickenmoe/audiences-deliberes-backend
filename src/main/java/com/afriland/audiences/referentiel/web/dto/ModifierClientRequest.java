package com.afriland.audiences.referentiel.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /clients/{id}}: lets a typo in a name or a changed email be corrected.
 *
 * <p><b>The reference is deliberately absent.</b> It is the client's identifier, used for search,
 * and must remain the key of a future external reference system (INT-05). Changing it would alter
 * an identifier known to its users.
 */
public record ModifierClientRequest(
        @NotBlank @Size(max = 255) String nom,
        @Email @Size(max = 255) String email,
        @Size(max = 20) String telephone) {
}
