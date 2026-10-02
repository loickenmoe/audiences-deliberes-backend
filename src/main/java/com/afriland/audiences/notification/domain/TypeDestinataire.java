package com.afriland.audiences.notification.domain;

/**
 * Nature du destinataire, qui dit à quelle table renvoie {@code destinataire_ref} : {@code utilisateur}
 * pour un utilisateur interne, {@code intervenant} pour un avocat ou un autre prestataire.
 */
public enum TypeDestinataire {
    UTILISATEUR,
    AVOCAT,
    AUTRE_PRESTATAIRE
}
