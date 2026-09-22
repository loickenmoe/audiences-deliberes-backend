package com.afriland.audiences.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Paramètres du Notification Engine, lus depuis le {@code .env} (jamais versionné).
 *
 * @param actif       interrupteur général ; {@code false} par défaut : aucune notification ne quitte
 *                    l'application, elles sont seulement écrites dans le journal
 * @param codeService code du compte de service attribué par l'administrateur ({@code sub} du JWT)
 * @param jwtSecret   secret de signature HS256 des jetons ; au moins 32 caractères (Guide V1 §4 bis)
 */
@ConfigurationProperties(prefix = "notification.engine")
public record NotificationEngineProperties(
        @DefaultValue("false") boolean actif,
        String codeService,
        String jwtSecret) {

    /** Longueur minimale du secret exigée par le moteur pour la signature HS256 (256 bits). */
    public static final int LONGUEUR_MIN_SECRET = 32;

    /** Masque le secret : ce record peut finir dans un journal ou un message d'erreur. */
    @Override
    public String toString() {
        return "NotificationEngineProperties[actif=" + actif + ", codeService=" + codeService
                + ", jwtSecret=" + (jwtSecret == null || jwtSecret.isBlank() ? "(absent)" : "***") + "]";
    }
}
