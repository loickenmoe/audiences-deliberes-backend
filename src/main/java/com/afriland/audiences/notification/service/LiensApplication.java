package com.afriland.audiences.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Application links inserted in external notifications. The base URL depends on the environment
 * ({@code NOTIFICATION_LIEN_APPLICATION}); paths match the frontend screens.
 */
@Component
public class LiensApplication {

    private final String base;

    public LiensApplication(@Value("${notification.lien-application}") String base) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** Case-file page; without a case file, the user's notification list. */
    public String pourUtilisateur(Long dossierId) {
        return dossierId == null ? base + "/notifications" : base + "/dossiers/" + dossierId;
    }

    /** Portal of a lawyer who has an account; other providers have none. */
    public String portailAvocat() {
        return base + "/portail";
    }
}
