package com.afriland.audiences.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Paramètres du Notification Engine, lus depuis le {@code .env} (jamais versionné).
 *
 * @param actif          interrupteur général ; {@code false} par défaut : aucune notification ne quitte
 *                       l'application, elles sont seulement écrites dans le journal
 * @param codeService    code du compte de service attribué par l'administrateur ({@code sub} du JWT)
 * @param jwtSecret      secret de signature HS256 des jetons ; au moins 32 caractères (Guide V1 §4 bis)
 * @param tentativesMax  nombre de remises tentées avant l'échec définitif (D13)
 * @param ageMax         au-delà, une notification en attente est périmée plutôt qu'envoyée (voir § 8)
 * @param redirection     bac à sable des environnements de travail (D10)
 * @param callbackBaseUrl adresse publique de l'application, telle que le moteur peut la joindre. Vide
 *                        depuis un poste de travail : les rappels sont alors désactivés (Q-120)
 */
@ConfigurationProperties(prefix = "notification.engine")
public record NotificationEngineProperties(
        @DefaultValue("false") boolean actif,
        String codeService,
        String jwtSecret,
        @DefaultValue("5") int tentativesMax,
        @DefaultValue("2h") Duration ageMax,
        @DefaultValue Redirection redirection,
        @DefaultValue Transport transport,
        String callbackBaseUrl) {

    /** Longueur minimale du secret exigée par le moteur pour la signature HS256 (256 bits). */
    public static final int LONGUEUR_MIN_SECRET = 32;

    /**
     * Délais avant chaque nouvelle tentative (D13) : 1 min, 5 min, 15 min, 1 h, puis 6 h. Au-delà du
     * nombre de tentatives, la notification est en échec et visible en supervision.
     */
    public static final List<Duration> DELAIS_TENTATIVES = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15),
            Duration.ofHours(1), Duration.ofHours(6));

    /**
     * Transport du message jusqu'au moteur.
     *
     * @param type            DAPR (voie documentée par le Guide), KAFKA (repli direct) ou JOURNAL (rien
     *                        n'est envoyé, utile pour éprouver une configuration active sans émettre)
     * @param topic           topic STANDARD : ses messages sont rattrapés après une panne, contrairement
     *                        au topic CRITICAL, qui les jette (D8)
     * @param pubsub          nom du composant Dapr qui porte ce topic
     * @param daprUrl         adresse locale du sidecar
     * @param attenteSecondes délai d'attente de l'accusé du courtier, en transport Kafka direct
     */
    public record Transport(
            @DefaultValue("DAPR") TypeTransport type,
            @DefaultValue("notification-send-standard") String topic,
            @DefaultValue("notification-pubsub-standard") String pubsub,
            @DefaultValue("http://localhost:3500") String daprUrl,
            @DefaultValue("10") long attenteSecondes) {
    }

    public enum TypeTransport {
        DAPR, KAFKA, JOURNAL
    }

    /** Masque le secret : ce record peut finir dans un journal ou un message d'erreur. */
    @Override
    public String toString() {
        return "NotificationEngineProperties[actif=" + actif + ", codeService=" + codeService
                + ", jwtSecret=" + (jwtSecret == null || jwtSecret.isBlank() ? "(absent)" : "***")
                + ", tentativesMax=" + tentativesMax + ", ageMax=" + ageMax + ", redirection=" + redirection + "]";
    }

    /**
     * Redirection des envois hors production (D10). Les données de DEV contiennent de vrais numéros
     * d'avocats : tant qu'elle est active, aucun message ne part vers un destinataire réel, et le
     * destinataire d'origine est conservé dans la file pour le contrôle.
     *
     * @param courrielInterne   remplace l'adresse professionnelle (Outlook, Teams)
     * @param courrielPersonnel remplace le courriel personnel d'un prestataire
     * @param telephone         remplace le numéro (SMS, WhatsApp), au format du Guide
     */
    public record Redirection(
            @DefaultValue("true") boolean actif,
            String courrielInterne,
            String courrielPersonnel,
            String telephone) {

        public boolean complete() {
            return estRenseigne(courrielInterne) && estRenseigne(courrielPersonnel) && estRenseigne(telephone);
        }

        private static boolean estRenseigne(String valeur) {
            return valeur != null && !valeur.isBlank();
        }
    }
}
