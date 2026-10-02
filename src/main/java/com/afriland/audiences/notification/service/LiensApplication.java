package com.afriland.audiences.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Liens vers l'application insérés dans les notifications externes. L'adresse de base dépend de
 * l'environnement ({@code NOTIFICATION_LIEN_APPLICATION}) ; les chemins sont ceux des écrans du
 * frontend.
 */
@Component
public class LiensApplication {

    private final String base;

    public LiensApplication(@Value("${notification.lien-application}") String base) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** Fiche du dossier ; à défaut de dossier, la liste des notifications de l'utilisateur. */
    public String pourUtilisateur(Long dossierId) {
        return dossierId == null ? base + "/notifications" : base + "/dossiers/" + dossierId;
    }

    /** Espace de l'avocat qui a un compte ; un autre prestataire n'en a pas. */
    public String portailAvocat() {
        return base + "/portail";
    }
}
