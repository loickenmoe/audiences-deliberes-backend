package com.afriland.audiences.intervenant.domain;

/**
 * Participant type that can be created through the API; mirrors the discriminator of the
 * {@code Intervenant} hierarchy (RG-INT-01/02).
 */
public enum TypeIntervenant {
    AVOCAT,
    AUTRE_PRESTATAIRE
}
