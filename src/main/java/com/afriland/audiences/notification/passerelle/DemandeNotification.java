package com.afriland.audiences.notification.passerelle;

import java.util.Map;
import java.util.Objects;

/**
 * Notification prête à être remise au Notification Engine, indépendante du transport.
 *
 * @param eventId                clé de déduplication côté moteur (Guide V1 §5) : déterministe, dérivée
 *                               de la référence métier, pour qu'une republication ne double pas l'envoi
 * @param destinatairesParCanal  un destinataire par canal ({@code channelRecipients}) ; les canaux
 *                               envoyés sont exactement les clés de cette table
 * @param sujet                  objet du message (courriel et Teams) ; ignoré par SMS et WhatsApp
 * @param corps                  texte du message, déjà rédigé pour le canal le plus contraint
 */
public record DemandeNotification(
        String eventId,
        Map<CanalEngine, String> destinatairesParCanal,
        String sujet,
        String corps) {

    public DemandeNotification {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId obligatoire : il sert à la déduplication côté moteur");
        }
        Objects.requireNonNull(destinatairesParCanal, "destinatairesParCanal");
        if (destinatairesParCanal.isEmpty()) {
            throw new IllegalArgumentException("Au moins un canal avec son destinataire est requis");
        }
        if (corps == null || corps.isBlank()) {
            throw new IllegalArgumentException("Le corps du message est obligatoire");
        }
        destinatairesParCanal = Map.copyOf(destinatairesParCanal);
    }
}
