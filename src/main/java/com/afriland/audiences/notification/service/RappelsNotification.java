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
 * Callback URL the Notification Engine calls after processing a message ({@code callbackUrl},
 * Guide V1 §7.9), and its verification.
 * <p>
 * This is the only exact way to know which notification succeeded, since the tracking topics do not
 * return our reference; it is therefore placed in the URL.
 * <p>
 * The callback is unauthenticated, so the URL carries an HMAC-SHA256 signature of the reference using
 * the service account secret, compared in constant time. Otherwise anyone could mark a notification
 * delivered.
 * <p>
 * Disabled while no public URL is configured: the engine cannot reach a workstation.
 */
@Component
@RequiredArgsConstructor
public class RappelsNotification {

    static final String CHEMIN = "/notifications/accuses/rappel/";
    public static final String PARAMETRE_SIGNATURE = "signature";

    private final NotificationEngineProperties proprietes;

    /** URL to send to the engine, or empty if callbacks are not configured. */
    public Optional<String> urlPour(String eventId) {
        String base = proprietes.callbackBaseUrl();
        if (base == null || base.isBlank()) {
            return Optional.empty();
        }
        String racine = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return Optional.of(racine + CHEMIN + URLEncoder.encode(eventId, StandardCharsets.UTF_8)
                + "?" + PARAMETRE_SIGNATURE + "=" + signer(eventId));
    }

    /** Constant-time comparison, so timing does not leak the signature. */
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
