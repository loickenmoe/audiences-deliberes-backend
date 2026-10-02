package com.afriland.audiences.notification.domain;

/** Cycle de vie d'une ligne de {@code notification_envoi} (voir la migration V27). */
public enum StatutEnvoi {
    /** À remettre au transport dès que {@code prochaineTentative} est atteinte. */
    EN_ATTENTE,
    /**
     * Une pièce jointe doit être déposée avant l'envoi (D5) : la clé de stockage a été demandée au
     * moteur, et la ligne attend sa réponse. Elle repart en EN_ATTENTE dès que la clé est connue.
     */
    ATTENTE_PIECE_JOINTE,
    /** Remise au transport ; l'accusé de l'Engine n'est pas encore arrivé. */
    PUBLIEE,
    /** Accusé positif reçu sur {@code notification-sent}. */
    ENVOYEE,
    /** Tentatives épuisées, ou échec définitif signalé par l'Engine (D13). */
    ECHEC,
    /** Canal routé, mais aucune coordonnée pour ce destinataire : rien n'est envoyé (D14). */
    NON_JOIGNABLE,

    /**
     * Remise faite, mais aucun accusé du moteur dans le délai attendu (Q-120) : ni confirmé, ni en
     * échec. C'est l'état d'un message rejeté en silence.
     */
    SANS_RETOUR
}
