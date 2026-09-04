package com.afriland.audiences.referentiel.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 🆕 Endpoint {@code POST /clients} absent du contrat d'API alors que {@code dossier.client_id}
 * est {@code NOT NULL} et que les données clients peuvent être saisies manuellement (US §11.3,
 * INT-05) — cf. DECISIONS_AND_OPEN_QUESTIONS.md Q-23. Sans lui, aucun dossier n'est créable.
 */
public record CreerClientRequest(
        @NotBlank @Size(max = 50) String reference,
        @NotBlank @Size(max = 255) String nom,
        @Email @Size(max = 255) String email,
        @Size(max = 20) String telephone) {
}
