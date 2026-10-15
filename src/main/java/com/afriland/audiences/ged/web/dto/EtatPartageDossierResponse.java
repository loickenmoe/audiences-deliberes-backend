package com.afriland.audiences.ged.web.dto;

/**
 * Ce que le partage d'une pièce produira chez l'avocat, sur ce dossier (QF-65).
 *
 * <p>{@code avocatConstitue} est vrai si au moins un avocat <b>affecté</b> au dossier y est
 * <b>constitué</b> - la règle même qui lui ouvre les pièces (Q-139). Faux, une pièce partagée
 * attend la signature d'une lettre : celui qui partage doit le savoir, sans quoi il lit
 * « Partagé » quand l'avocat ne voit rien.
 */
public record EtatPartageDossierResponse(boolean avocatConstitue) {
}
