package com.afriland.audiences.dossier.domain;

import org.springframework.data.domain.Sort;

/**
 * Les tris offerts sur {@code GET /dossiers} (observation métier du 2026-09-25 : « trier les
 * dossiers en fonction de leur étape et pendant une période donnée »).
 *
 * <p><b>Un vocabulaire fermé, et non un nom de colonne.</b> Spring Data sait construire un
 * {@code Sort} à partir d'une chaîne reçue du client, mais cela reviendrait à laisser le navigateur
 * nommer les champs de l'entité : une propriété inconnue provoque une erreur 500, et les noms
 * acceptés dessinent le schéma pour qui les essaie. Un énuméré ne peut prendre que ces quatre
 * valeurs, et chacune dit ce qu'elle trie en termes métier plutôt qu'en termes de table.
 *
 * <p><b>L'identifiant ferme chaque tri.</b> Sans lui, deux dossiers ouverts la même seconde — ce qui
 * arrive à la reprise de données — peuvent changer de place entre deux pages, et une ligne se
 * retrouve alors affichée deux fois ou jamais.
 *
 * <p><b>Pourquoi pas de tri par étape</b>, alors que le métier l'a formulé ainsi ? Parce qu'il
 * demande en réalité de regrouper, et que le filtre par type et statut d'étape y répond mieux : une
 * fois « appel en cours » choisi, la liste ne contient plus que des appels en cours, et les trier
 * entre eux par étape n'apprendrait rien. Trier sur l'étape supposerait en outre d'en élire une par
 * dossier, alors que les deux cassations sont concurrentes — l'ordre dépendrait d'un arbitrage
 * invisible.
 */
public enum TriDossiers {

    /** Les dossiers ouverts le plus récemment d'abord. Le défaut, inchangé depuis Q-93. */
    RECENT(Sort.by(Sort.Direction.DESC, "createdAt", "id")),

    /** Les plus anciens d'abord : ce qui traîne depuis le plus longtemps. */
    ANCIEN(Sort.by(Sort.Direction.ASC, "createdAt", "id")),

    /** Par référence, dans l'ordre alphabétique — l'ordre du classement papier. */
    REFERENCE(Sort.by(Sort.Direction.ASC, "reference").and(Sort.by(Sort.Direction.ASC, "id"))),

    /** Le risque encouru le plus élevé d'abord : l'ordre dans lequel la banque les regarde. */
    RISQUE(Sort.by(Sort.Direction.DESC, "risqueEncouru", "id"));

    private final Sort sort;

    TriDossiers(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }
}
