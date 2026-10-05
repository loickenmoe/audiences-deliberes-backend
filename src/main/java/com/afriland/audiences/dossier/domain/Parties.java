package com.afriland.audiences.dossier.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Parties au procès : demandeurs et défendeurs.
 *
 * <p><b>Plusieurs de chaque depuis le 2026-10-05</b> (métier, observations 1 et 3). Un procès
 * oppose rarement une personne à une autre : une banque assigne un débiteur et sa caution, se
 * défend contre plusieurs héritiers. Les champs uniques d'origine obligeaient à tout écrire dans
 * une phrase, et ce qui est écrit dans une phrase ne se compte ni ne se retrouve.
 *
 * <p>Le client principal reste référencé par {@code dossier.client_id} et <b>n'est pas répété
 * ici</b> (Q-27) : il est partie au procès, mais c'est son identité au référentiel qui fait foi,
 * pas une chaîne recopiée.
 *
 * <p>Stocké en JSONB sur {@code dossier.parties}, via {@code @JdbcTypeCode(SqlTypes.JSON)} sur
 * l'entité {@code Dossier}. Le passage d'une valeur unique à une liste n'a donc demandé aucune
 * colonne nouvelle — seulement une migration du contenu (V41).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Parties {

    /** Au moins un, jamais vide, depuis le 2026-10-05. Jamais {@code null} : une liste vide sinon. */
    private List<String> demandeurs = new ArrayList<>();

    /** Au moins un, jamais vide, depuis le 2026-10-05. Jamais {@code null} : une liste vide sinon. */
    private List<String> defendeurs = new ArrayList<>();
}
