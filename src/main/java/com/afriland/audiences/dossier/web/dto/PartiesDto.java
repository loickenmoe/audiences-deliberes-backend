package com.afriland.audiences.dossier.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Les parties au procès, telles que l'écran les envoie et les relit.
 *
 * <p><b>Obligatoires depuis le 2026-10-05</b> (métier, observation 1) et <b>multiples</b>
 * (observation 3). Un dossier contentieux sans demandeur ni défendeur ne dit pas qui s'oppose à
 * qui : l'information manquait sur 340 des 341 dossiers de recette, précisément parce que rien ne
 * l'exigeait.
 *
 * <p>La contrainte est portée ici, et non dans le service : un refus à l'entrée nomme le champ
 * fautif, ce que l'écran sait afficher sous le bon bloc. Une vérification plus loin rendrait un
 * message global que l'utilisateur devrait interpréter.
 */
public record PartiesDto(
        @NotEmpty(message = "Au moins un demandeur est obligatoire")
        List<@NotBlank @Size(max = 255) String> demandeurs,

        @NotEmpty(message = "Au moins un défendeur est obligatoire")
        List<@NotBlank @Size(max = 255) String> defendeurs) {
}
