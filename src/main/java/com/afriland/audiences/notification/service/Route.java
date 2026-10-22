package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.passerelle.CanalEngine;

import java.util.Objects;

/**
 * A channel selected by routing for a recipient. Without a usable address the route is kept with its
 * reason and becomes a NON_JOIGNABLE row, visible in monitoring.
 *
 * @param reglage     channel as configured (OUTLOOK, TEAMS, COURRIEL_PERSONNEL, SMS, WHATSAPP)
 * @param canal       matching Notification Engine channel
 * @param destinataire formatted address or number; {@code null} if unreachable
 * @param motif       why there is no recipient; {@code null} if reachable
 */
public record Route(String reglage, CanalEngine canal, String destinataire, MotifNonJoignable motif) {

    public Route {
        Objects.requireNonNull(reglage, "reglage");
        Objects.requireNonNull(canal, "canal");
        if ((destinataire == null) == (motif == null)) {
            throw new IllegalArgumentException("Une route a soit un destinataire, soit un motif de non-joignabilité");
        }
    }

    public boolean joignable() {
        return destinataire != null;
    }

    public enum MotifNonJoignable {
        /** No contact detail recorded for this channel. */
        COORDONNEE_ABSENTE,
        /** A contact detail exists, but in a format the Notification Engine rejects. */
        COORDONNEE_INVALIDE
    }
}
