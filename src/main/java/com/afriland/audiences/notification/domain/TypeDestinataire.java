package com.afriland.audiences.notification.domain;

/**
 * Recipient kind, telling which table {@code destinataire_ref} points to: {@code utilisateur} for an
 * internal user, {@code intervenant} for a lawyer or another external provider.
 */
public enum TypeDestinataire {
    UTILISATEUR,
    AVOCAT,
    AUTRE_PRESTATAIRE
}
