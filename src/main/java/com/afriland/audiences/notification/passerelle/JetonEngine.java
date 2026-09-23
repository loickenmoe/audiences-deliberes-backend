package com.afriland.audiences.notification.passerelle;

import com.afriland.audiences.notification.config.NotificationEngineProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Jeton court qui identifie le service appelant dans chaque message Kafka (Guide V1 §4 bis) :
 * HS256, {@code sub} = code du service, durée de vie de 5 minutes.
 * <p>
 * <b>Le secret est utilisé tel quel</b>, c'est-à-dire les octets UTF-8 de la chaîne partagée. Le
 * Guide exige « au moins 32 caractères », une longueur qui n'aurait pas de sens si la chaîne était
 * d'abord décodée en base64. <b>À confirmer au premier envoi réel</b> : un jeton invalide est rejeté
 * par le moteur <b>sans aucun retour</b>, et seule l'absence d'accusé le révélerait.
 * <p>
 * Un jeton est produit à chaque remise, et non mis en cache : il vaut 5 minutes, et une notification
 * retentée six heures plus tard doit en porter un valide.
 */
@Component
public class JetonEngine {

    static final Duration VALIDITE = Duration.ofMinutes(5);

    private final NotificationEngineProperties proprietes;

    public JetonEngine(NotificationEngineProperties proprietes) {
        this.proprietes = proprietes;
    }

    public String signer() {
        String secret = proprietes.jwtSecret();
        if (secret == null || secret.length() < NotificationEngineProperties.LONGUEUR_MIN_SECRET) {
            throw new IllegalStateException("Secret de signature absent ou trop court : le moteur rejetterait le message");
        }
        Instant maintenant = Instant.now();
        JWTClaimsSet revendications = new JWTClaimsSet.Builder()
                .subject(proprietes.codeService())
                .issueTime(Date.from(maintenant))
                .expirationTime(Date.from(maintenant.plus(VALIDITE)))
                .build();
        SignedJWT jeton = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), revendications);
        try {
            jeton.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (JOSEException e) {
            throw new IllegalStateException("Signature du jeton impossible", e);
        }
        return jeton.serialize();
    }
}
