package com.afriland.audiences.referentiel.web.dto;

import com.afriland.audiences.referentiel.domain.TypeValeurConfiguration;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Paramètre système (#47, #48). Les quatre derniers champs décrivent la <b>règle de saisie</b> de
 * la clé (Q-91) pour que l'écran d'administration construise le champ adéquat : bornes d'un entier,
 * ou valeurs acceptées d'une liste. Ils sont nuls / vides pour une clé sans règle déclarée.
 */
public record ConfigurationResponse(
        String cle,
        String valeur,
        String description,
        String modifiablePar,
        LocalDateTime dateModification,
        TypeValeurConfiguration typeValeur,
        Long valeurMinimale,
        Long valeurMaximale,
        List<String> valeursPossibles) {
}
