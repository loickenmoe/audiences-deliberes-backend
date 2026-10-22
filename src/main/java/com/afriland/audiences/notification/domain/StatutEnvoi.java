package com.afriland.audiences.notification.domain;

/** Lifecycle of a {@code notification_envoi} row (migration V27). */
public enum StatutEnvoi {
    /** To hand to the transport once {@code prochaineTentative} is reached. */
    EN_ATTENTE,
    /**
     * An attachment must be uploaded before sending: the storage key was requested from the engine and
     * the row awaits the response. It returns to EN_ATTENTE once the key is known.
     */
    ATTENTE_PIECE_JOINTE,
    /** Handed to the transport; the Engine receipt has not arrived yet. */
    PUBLIEE,
    /** Positive receipt received on {@code notification-sent}. */
    ENVOYEE,
    /** Attempts exhausted, or permanent failure reported by the Engine. */
    ECHEC,
    /** Channel routed but no contact detail for this recipient: nothing is sent. */
    NON_JOIGNABLE,

    /**
     * Delivered to the transport but no engine receipt within the expected delay: neither confirmed nor
     * failed. This is the state of a silently rejected message.
     */
    SANS_RETOUR
}
