package com.afriland.audiences.publication.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ce que la direction écrit à un avocat (N7 temps 2).
 *
 * @param dossierId facultatif : la demande porte sur une conduite, pas sur une affaire. Renseigné,
 *                  il situe l'échange et donne à l'historique le dossier sur lequel s'inscrire
 */
public record DemanderExplicationRequest(
        @NotNull Long avocatId,
        Long dossierId,
        @NotBlank @Size(max = 4000) String contenu) {
}
