package com.afriland.audiences.dossier.web.dto;

import com.afriland.audiences.dossier.domain.TypeEtape;
import jakarta.validation.constraints.NotNull;

/**
 * Création directe d'une étape (UC-DOS-04 alt. 3-a/3-b : antécédents inconnus). Les étapes
 * intermédiaires manquantes sont créées automatiquement au statut OUVERTURE.
 * <p>
 * 🆕 Endpoint absent du contrat d'API (qui ne permet de modifier que le statut d'une étape
 * <em>existante</em> via son {@code etapeId}) — nécessaire pour permettre de créer directement
 * une étape ultérieure sans passer par les étapes intermédiaires (RG-DOS-04). Non tracké comme
 * l'un des 12 endpoints officiels de ce jalon ; ajouté par nécessité fonctionnelle documentée.
 */
public record CreerEtapeRequest(@NotNull TypeEtape type) {
}
