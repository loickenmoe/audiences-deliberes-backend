package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.passerelle.CanalEngine;

import java.util.Objects;

/**
 * Un canal retenu par le routage pour un destinataire. Sans destinataire utilisable, la route reste
 * listée avec son motif : elle deviendra une ligne NON_JOIGNABLE, visible en supervision (D14).
 *
 * @param reglage     canal tel que le DJ le règle (OUTLOOK, TEAMS, COURRIEL_PERSONNEL, SMS, WHATSAPP)
 * @param canal       canal du Notification Engine correspondant
 * @param destinataire adresse ou numéro mis en forme ; {@code null} si non joignable
 * @param motif       raison de l'absence de destinataire ; {@code null} si joignable
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
        /** Aucune coordonnée enregistrée pour ce canal. */
        COORDONNEE_ABSENTE,
        /** Une coordonnée existe, mais dans une forme que le Notification Engine n'accepte pas. */
        COORDONNEE_INVALIDE
    }
}
