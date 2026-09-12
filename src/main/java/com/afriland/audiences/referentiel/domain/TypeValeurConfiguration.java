package com.afriland.audiences.referentiel.domain;

/**
 * Nature de la valeur attendue par une clé de {@code configuration_systeme} (Q-91). Exposée dans
 * {@code ConfigurationResponse} pour que l'écran d'administration construise le bon champ de
 * saisie à partir du contrat, sans redéclarer les règles de son côté.
 */
public enum TypeValeurConfiguration {

    /** Entier positif, borné (ex. {@code SEUIL_PROROGATIONS}, {@code TAILLE_MAX_GED}). */
    ENTIER,

    /** Liste de valeurs séparées par des virgules, prises dans un ensemble fermé (ex. {@code FORMATS_GED}). */
    LISTE
}
