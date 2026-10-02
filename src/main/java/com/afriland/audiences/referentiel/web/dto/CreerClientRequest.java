package com.afriland.audiences.referentiel.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 🆕 Endpoint {@code POST /clients} absent du contrat d'API alors que {@code dossier.client_id}
 * est {@code NOT NULL} et que les données clients peuvent être saisies manuellement (US §11.3,
 * INT-05) — cf. DECISIONS_AND_OPEN_QUESTIONS.md Q-23. Sans lui, aucun dossier n'est créable.
 * <p>
 * <b>La référence n'est pas dans cette requête</b> : le système l'attribue ({@code CLI-2026-0001}),
 * sur demande du métier du 2026-09-29. Elle ne l'était déjà plus en modification (Q-116), pour une
 * raison qui vaut aussi ici : elle est l'identifiant du client, et un identifiant saisi à la main
 * porte des coquilles que Q-116 rend ensuite définitives.
 * <p>
 * <b>Réserve connue.</b> US §11.3 prévoit que les données clients « peuvent provenir d'un
 * référentiel externe ou être saisies manuellement », et Q-116 veut que cette référence reste « la
 * clé d'un futur référentiel externe ». Un client déjà connu de la banque porte donc une référence
 * <b>imposée</b>, que le système ne peut plus reprendre. Le jour où INT-05 sera raccordé, il faudra
 * rouvrir ce point — vraisemblablement par un second champ portant la clé externe, plutôt qu'en
 * rendant de nouveau la référence saisissable.
 */
public record CreerClientRequest(
        @NotBlank @Size(max = 255) String nom,
        @Email @Size(max = 255) String email,
        @Size(max = 20) String telephone) {
}
