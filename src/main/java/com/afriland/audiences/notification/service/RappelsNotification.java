package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.config.NotificationEngineProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

/**
 * Adresse que le Notification Engine rappelle après avoir traité un message ({@code callbackUrl},
 * Guide V1 §7.9), et vérification de ce rappel.
 * <p>
 * <b>C'est la seule façon exacte de savoir quelle notification a abouti</b> : les topics de suivi
 * ne renvoient pas notre référence (Q-120). Elle est donc placée dans l'URL elle-même.
 * <p>
 * <b>Le rappel arrive sans authentification</b> — le Guide n'en décrit aucune. L'URL porte donc une
 * signature HMAC-SHA256 de la référence, calculée avec le secret du compte de service : seul un
 * appelant connaissant ce secret peut forger une adresse valable, et la comparaison se fait en temps
 * constant. Sans cela, n'importe qui pourrait déclarer une notification délivrée.
 * <p>
 * Le rappel reste <b>désactivé tant qu'aucune adresse publique n'est configurée</b> : depuis un poste
 * de travail, le moteur ne peut pas joindre l'application, et annoncer une adresse injoignable ne
 * ferait qu'ajouter des erreurs de son côté.
 */
@Component
@RequiredArgsConstructor
public class RappelsNotification {

    static final String CHEMIN = "/notifications/accuses/rappel/";
    public static final String PARAMETRE_SIGNATURE = "signature";

    private final NotificationEngineProperties proprietes;

    /** URL à transmettre au moteur, ou vide si les rappels ne sont pas configurés. */
    public Optional<String> urlPour(String eventId) {
        String base = proprietes.callbackBaseUrl();
        if (base == null || base.isBlank()) {
            return Optional.empty();
        }
        String racine = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return Optional.of(racine + CHEMIN + URLEncoder.encode(eventId, StandardCharsets.UTF_8)
                + "?" + PARAMETRE_SIGNATURE + "=" + signer(eventId));
    }

    /** Comparaison en temps constant : une comparaison ordinaire laisserait deviner la signature. */
    public boolean signatureValide(String eventId, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(signer(eventId).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    String signer(String eventId) {
        String secret = proprietes.jwtSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Signature des rappels impossible : secret du compte de service absent");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(eventId.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Signature des rappels impossible", e);
        }
    }
}
