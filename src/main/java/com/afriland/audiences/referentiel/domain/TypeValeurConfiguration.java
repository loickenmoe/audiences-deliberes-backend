package com.afriland.audiences.referentiel.domain;

/**
 * Kind of value expected by a {@code configuration_systeme} key. Exposed in
 * {@code ConfigurationResponse} so the admin screen builds the right input field from the
 * contract, without redeclaring the rules.
 */
public enum TypeValeurConfiguration {

    /** Bounded positive integer (e.g. {@code SEUIL_PROROGATIONS}, {@code TAILLE_MAX_GED}). */
    ENTIER,

    /** Comma-separated list of values from a closed set (e.g. {@code FORMATS_GED}). */
    LISTE
}
