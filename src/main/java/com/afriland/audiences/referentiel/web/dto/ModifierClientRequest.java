package com.afriland.audiences.referentiel.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /clients/{id}} — recommandé dès Q-23, différé faute de besoin, ouvert quand le besoin
 * est apparu : une coquille dans un nom ou un e-mail changé ne pouvaient plus être corrigés.
 *
 * <p><b>La référence n'y figure pas, et c'est voulu.</b> Elle est l'identifiant du client : c'est elle
 * qu'on recherche, et elle doit rester la clé d'un futur référentiel externe (INT-05). La modifier
 * ferait changer un identifiant sous les yeux de ceux qui le connaissaient. Décision du porteur du
 * projet (2026-09-22).
 */
public record ModifierClientRequest(
        @NotBlank @Size(max = 255) String nom,
        @Email @Size(max = 255) String email,
        @Size(max = 20) String telephone) {
}
