package com.afriland.audiences.dossier.web.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** FR-DOS-02, US 1.2. Listes complètes (remplacent l'affectation existante), pas un delta. */
/**
 * Nouvelle affectation d'un dossier (FR-DOS-02). L'endpoint <b>remplace</b> les deux listes.
 * <p>
 * {@code @NotEmpty} ajouté en M16 (Q-75) : sans lui, une liste vide ou absente retirait tous les
 * juristes — ou tous les avocats — d'un dossier, contre RG-DOS-03, alors que la création exige déjà
 * les deux listes non vides. Même règle aux deux bouts de la vie de l'affectation.
 */
public record ModifierAffectationRequest(
        @NotEmpty List<Long> juristesAffectes,
        @NotEmpty List<Long> avocatsAffectes) {
}
