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
 * @param priorite        priorité portée par chaque message ; celle du compte de service (STANDARD)
 * @param delaiDepot      au-delà, une notification qui attend l'adresse de dépôt de sa pièce jointe
 *                        part sans elle (D5) : prévenir sans la pièce vaut mieux que ne pas prévenir
 * @param suiviAccuses    le moteur publie-t-il vraiment ses accusés ? <b>Faux par défaut</b> : relus
 *                        depuis leur origine, {@code notification-sent} et {@code notification-failed}
 *                        se sont révélés vides, tous services confondus (Q-121). Tant qu'il en est
 *                        ainsi, une notification remise ne peut pas être confirmée, et l'attendre
 *                        ferait passer en SANS_RETOUR des messages pourtant délivrés
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
        String callbackBaseUrl,
        @DefaultValue("STANDARD") String priorite,
        @DefaultValue("false") boolean suiviAccuses,
        @DefaultValue("5m") Duration delaiDepot) {

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
     * @param topic           topic d'entrée du moteur. <b>Un message réellement accepté arrive sur
     *                        {@code notification-send-request}</b> (Q-121) : c'est le topic consommé, et
     *                        la priorité voyage dans le message plutôt que par un topic dédié. Le
     *                        {@code notification-send-standard} du Guide n'est lu par personne.
     * @param pubsub          nom du composant pubsub, tel qu'il apparaît dans les messages acceptés
     * @param daprUrl         adresse locale du sidecar
     * @param attenteSecondes délai d'attente de l'accusé du courtier, en transport Kafka direct
     * @param cloudEvents     en transport Kafka direct, reproduire l'enveloppe que le sidecar Dapr
     *                        aurait posée. Vrai par défaut : c'est ce que le moteur attend
     */
    public record Transport(
            @DefaultValue("DAPR") TypeTransport type,
            @DefaultValue("notification-send-request") String topic,
            @DefaultValue("kafka-pubsub") String pubsub,
            @DefaultValue("http://localhost:3500") String daprUrl,
            @DefaultValue("10") long attenteSecondes,
            @DefaultValue("true") boolean cloudEvents,
            @DefaultValue("attachment-upload-request") String topicDepot) {
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
