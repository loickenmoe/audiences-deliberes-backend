package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.config.NotificationEngineProperties;
import com.afriland.audiences.notification.config.NotificationEngineProperties.Redirection;
import com.afriland.audiences.notification.config.NotificationEngineProperties.Transport;
import com.afriland.audiences.notification.config.NotificationEngineProperties.TypeTransport;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tracking topics do not return our reference. The callback URL carries it, and its signature
 * prevents a third party from declaring a notification delivered.
 */
class RappelsNotificationTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private static RappelsNotification rappels(String base) {
        return new RappelsNotification(new NotificationEngineProperties(true, "audience-deliberer", SECRET, 5,
                Duration.ofHours(2), new Redirection(false, null, null, null),
                new Transport(TypeTransport.DAPR, "t", "p", "u", 10, true, "attachment-upload-request"), base, "STANDARD", false, java.time.Duration.ofMinutes(5)));
    }

    @Test
    void l_url_porte_la_reference_et_sa_signature() {
        String url = rappels("https://audiences.afriland.cm/").urlPour("alerte-42:SMS").orElseThrow();

        assertThat(url).startsWith("https://audiences.afriland.cm/notifications/accuses/rappel/alerte-42%3ASMS?signature=")
                .doesNotContain(SECRET);
    }

    /** From a workstation, the engine cannot reach the application: nothing is announced. */
    @Test
    void sans_adresse_publique_aucun_rappel_n_est_demande() {
        assertThat(rappels(null).urlPour("alerte-42:SMS")).isEmpty();
        assertThat(rappels("  ").urlPour("alerte-42:SMS")).isEmpty();
    }

    @Test
    void la_signature_est_propre_a_chaque_reference_et_verifiee() {
        RappelsNotification rappels = rappels("https://audiences.afriland.cm");
        String signature = rappels.signer("alerte-42:SMS");

        assertThat(rappels.signatureValide("alerte-42:SMS", signature)).isTrue();
        assertThat(rappels.signatureValide("alerte-43:SMS", signature)).isFalse();
        assertThat(rappels.signatureValide("alerte-42:SMS", "forgee")).isFalse();
        assertThat(rappels.signatureValide("alerte-42:SMS", null)).isFalse();
    }

    /** Another secret produces another signature: the callback cannot be forged from outside. */
    @Test
    void un_secret_different_ne_valide_pas_la_signature() {
        String signatureAutre = new RappelsNotification(new NotificationEngineProperties(true, "audience-deliberer",
                "fedcba9876543210fedcba9876543210", 5, Duration.ofHours(2), new Redirection(false, null, null, null),
                new Transport(TypeTransport.DAPR, "t", "p", "u", 10, true, "attachment-upload-request"), "https://x", "STANDARD", false, java.time.Duration.ofMinutes(5))).signer("alerte-42:SMS");

        assertThat(rappels("https://audiences.afriland.cm").signatureValide("alerte-42:SMS", signatureAutre)).isFalse();
    }
}
