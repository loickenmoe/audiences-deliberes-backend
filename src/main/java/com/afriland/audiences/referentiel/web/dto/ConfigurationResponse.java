package com.afriland.audiences.referentiel.web.dto;

import com.afriland.audiences.referentiel.domain.TypeValeurConfiguration;

import java.time.LocalDateTime;
import java.util.List;

/**
 * System parameter. The last four fields describe the key's <b>input rule</b> so the admin screen
 * builds the right field: integer bounds or accepted list values. They are null/empty for a key
 * without a declared rule. {@code listeVideAutorisee} tells whether a list may be fully unchecked
 * (notification settings: no channel or no event is a valid choice).
 */
public record ConfigurationResponse(
        String cle,
        String valeur,
        String description,
        String modifiablePar,
        LocalDateTime dateModification,
        Long dernierAuteurId,
        TypeValeurConfiguration typeValeur,
        Long valeurMinimale,
        Long valeurMaximale,
        List<String> valeursPossibles,
        boolean listeVideAutorisee) {
}
