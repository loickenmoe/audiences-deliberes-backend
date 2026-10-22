package com.afriland.audiences.notification.passerelle;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * CloudEvents 1.0 envelope that the Dapr sidecar puts around a published message.
 * <p>
 * The Notification Engine consumes through Dapr, which unwraps this envelope; publishing to Kafka
 * without it would rely on the engine's tolerance, and an ignored message leaves no trace
 * (Guide V1 §4 bis). Since the sidecar cannot be installed on workstations, we produce the same
 * envelope. Field names follow the CloudEvents/Dapr external contract.
 *
 * @param data the business message, as the engine will read it
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
        Object data) {

    /** Type Dapr gives to messages published through its API. */
    public static final String TYPE_DAPR = "com.dapr.event.sent";
    public static final String SPEC_VERSION = "1.0";
    public static final String CONTENT_TYPE = "application/json";

    public static EnveloppeCloudEvents autour(Object message, String source, String pubsub, String topic) {
        // Timestamp format copied from an accepted message: local time with offset, to the second.
        String horodatage = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String trace = nouveauTraceparent();
        return new EnveloppeCloudEvents(UUID.randomUUID().toString(), source, TYPE_DAPR, SPEC_VERSION, CONTENT_TYPE,
                topic, pubsub, horodatage, trace, trace, "", message);
    }

    /**
     * W3C trace context, as the sidecar sets it: version 00, 16-byte trace id, 8-byte span id and the
     * "sampled" flag.
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
