package com.afriland.audiences.referentiel.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /clients} is not in the API contract, but {@code dossier.client_id} is {@code NOT NULL}
 * and client data may be entered manually (US §11.3, INT-05): without it, no case file can be created.
 * <p>
 * <b>The reference is not part of this request</b>: the system assigns it ({@code CLI-2026-0001}).
 * It is the client's identifier, and a hand-typed identifier carries typos that would then be
 * permanent.
 * <p>
 * <b>Known limitation.</b> US §11.3 allows client data to come from an external reference system,
 * and the reference is meant to be its key. A client already known to the bank therefore has an
 * <b>imposed</b> reference the system cannot take over. When INT-05 is connected, this will need a
 * second field holding the external key rather than making the reference editable again.
 */
public record CreerClientRequest(
        @NotBlank @Size(max = 255) String nom,
        @Email @Size(max = 255) String email,
        @Size(max = 20) String telephone) {
}
