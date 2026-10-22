package com.afriland.audiences.alerte.domain;

import com.afriland.audiences.referentiel.domain.Utilisateur;

/**
 * Published by {@code AlerteService.declencher} once the alert is saved, <b>within the business
 * action's transaction</b>. Modules that extend an alert (external notifications) listen to it
 * without the alert module knowing them.
 * <p>
 * The {@code declencheur} text is deliberately left out: written for the screen, it may contain a
 * reason, a name or a technical marker, and must not travel beyond the in-app channel.
 */
public record AlerteDeclenchee(Long alerteId, TypeAlerte type, Long dossierId, Utilisateur destinataire) {
}
