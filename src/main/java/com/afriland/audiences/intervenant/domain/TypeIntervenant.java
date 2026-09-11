package com.afriland.audiences.intervenant.domain;

/**
 * Type d'intervenant créable via l'API (M15) — reprend le discriminant de la hiérarchie
 * {@code Intervenant} du référentiel (RG-INT-01/02).
 */
public enum TypeIntervenant {
    AVOCAT,
    AUTRE_PRESTATAIRE
}
