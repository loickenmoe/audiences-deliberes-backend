package com.afriland.audiences.notification.passerelle;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Enveloppe que le sidecar Dapr pose autour d'un message publié, au format CloudEvents 1.0.
 * <p>
 * <b>Pourquoi la reproduire nous-mêmes.</b> Le Notification Engine consomme ses topics par Dapr, qui
 * déballe cette enveloppe avant de livrer le message. Publier directement sur Kafka sans elle
 * reviendrait à parier sur la tolérance du moteur, et un message ignoré ne laisse aucune trace
 * (Guide V1 §4 bis). Le sidecar n'étant pas installable sur les postes — le registre d'images est
 * inaccessible depuis le réseau de la banque —, nous produisons la même enveloppe.
 * <p>
 * Les noms de champs sont ceux de CloudEvents et de Dapr : c'est un contrat externe.
 *
 * @param data le message métier, tel que le moteur le lira
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnveloppeCloudEvents(
        String id,
        String source,
        String type,
        String specversion,
        String datacontenttype,
        String topic,
        String pubsubname,
        String time,
        String traceid,
        String traceparent,
        String tracestate,
        MessageEngine data) {

    /** Type que Dapr donne aux messages publiés par son API. */
    public static final String TYPE = "com.dapr.event.sent";
    public static final String SPEC_VERSION = "1.0";
    public static final String CONTENT_TYPE = "application/json";

    public static EnveloppeCloudEvents autour(MessageEngine message, String source, String pubsub, String topic) {
        // Format d'horodatage repris d'un message accepté : heure locale avec décalage, à la seconde.
        String horodatage = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String trace = nouveauTraceparent();
        return new EnveloppeCloudEvents(UUID.randomUUID().toString(), source, TYPE, SPEC_VERSION, CONTENT_TYPE,
                topic, pubsub, horodatage, trace, trace, "", message);
    }

    /**
     * Contexte de trace W3C, tel que le sidecar en pose un : version 00, un identifiant de trace de
     * 16 octets, un identifiant de portée de 8 octets, et l'indicateur « échantillonné ».
     */
    private static String nouveauTraceparent() {
        RandomGenerator alea = RandomGenerator.getDefault();
        byte[] trace = new byte[16];
        byte[] portee = new byte[8];
        alea.nextBytes(trace);
        alea.nextBytes(portee);
        return "00-" + HexFormat.of().formatHex(trace) + "-" + HexFormat.of().formatHex(portee) + "-01";
    }
}
