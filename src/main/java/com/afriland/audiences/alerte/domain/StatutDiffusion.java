package com.afriland.audiences.alerte.domain;

/**
 * Outcome of the real-time delivery of an alert on the in-app channel (NF-ALR-02).
 * <p>Three values rather than a boolean: {@code SimpMessagingTemplate.convertAndSendToUser} throws
 * nothing when the recipient is offline, so a "push succeeded" flag would always read 100 %,
 * outages included. An absent recipient (normal) must be told apart from a broken channel (the
 * fault).
 * <p>{@link #DIFFUSEE} and {@link #DIFFEREE} both count as delivered: the in-app channel includes
 * the persisted notification list. Only {@link #ECHEC} is a loss.
 */
public enum StatutDiffusion {

    /** The recipient had a live WebSocket session and the push succeeded. */
    DIFFUSEE,

    /**
     * No open session when raised: the alert waits in {@code GET /alertes/mes-notifications}. Not a
     * failure, the fallback is guaranteed.
     */
    DIFFEREE,

    /**
     * The push failed (broker unavailable, broken transport). The only case NF-ALR-02 counts as a
     * loss.
     */
    ECHEC
}
