package com.afriland.audiences.notification.passerelle;

/**
 * Canal de diffusion du Notification Engine de la banque (Guide V1, champ {@code channels}). Le nom
 * de chaque constante est exactement la valeur attendue par le moteur, qui sert aussi de clé dans
 * {@code channelRecipients}.
 * <p>
 * Distinct de {@link com.afriland.audiences.alerte.domain.CanalAlerte} : ce dernier décrit la
 * diffusion côté application (APPLICATIF, OUTLOOK, TEAMS), celui-ci le transport externe. Outlook
 * y correspond au canal {@link #EMAIL} adressé à la messagerie professionnelle.
 */
public enum CanalEngine {
    EMAIL,
    SMS,
    WHATSAPP,
    TEAMS
}
