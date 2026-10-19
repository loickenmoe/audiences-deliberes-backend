package com.afriland.audiences.notification.passerelle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The published message format, compared to a message <b>actually accepted</b> by the engine.
 * <p>
 * The V1 Guide differs from what the engine really accepts, and a malformed message is rejected
 * <b>without any feedback</b>. The only defense is to freeze the observed shape here, field by field.
 */
class FormatMessageTest {

    /** Fields seen in an accepted message, except the subject (absent from an SMS). */
    private static final List<String> CHAMPS_ENVELOPPE = List.of("data", "datacontenttype", "id", "pubsubname",
            "source", "specversion", "time", "topic", "traceid", "traceparent", "tracestate", "type");
    private static final List<String> CHAMPS_MESSAGE = List.of("category", "channels", "directBody", "eventId",
            "priority", "recipient", "serviceKey", "token");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode publier(String sujet) throws Exception {
        MessageEngine message = MessageEngine.de("eyJhbGciOiJIUzI1NiJ9.jeton", "audience-deliberer", "STANDARD",
                CanalEngine.SMS, "+237600000000", sujet, "Texte du message", "alerte-42-SMS", null);
        return objectMapper.readTree(objectMapper.writeValueAsString(
                EnveloppeCloudEvents.autour(message, "audience-deliberer", "kafka-pubsub", "notification-send-request")));
    }

    @Test
    void l_enveloppe_porte_exactement_les_champs_d_un_message_accepte() throws Exception {
        JsonNode enveloppe = publier(null);

        assertThat(enveloppe.fieldNames()).toIterable().containsExactlyInAnyOrderElementsOf(CHAMPS_ENVELOPPE);
        assertThat(enveloppe.get("type").asText()).isEqualTo("com.dapr.event.sent");
        assertThat(enveloppe.get("specversion").asText()).isEqualTo("1.0");
        assertThat(enveloppe.get("datacontenttype").asText()).isEqualTo("application/json");
        assertThat(enveloppe.get("tracestate").asText()).isEmpty();
    }

    @Test
    void le_message_porte_exactement_les_champs_d_un_message_accepte() throws Exception {
        JsonNode data = publier(null).get("data");

        assertThat(data.fieldNames()).toIterable().containsExactlyInAnyOrderElementsOf(CHAMPS_MESSAGE);
        // Neither locale nor metadata: the Guide documents them, no accepted message carries them.
        assertThat(data.has("locale")).isFalse();
        assertThat(data.has("metadata")).isFalse();
        assertThat(data.has("channelRecipients")).isFalse();
        assertThat(data.get("recipient").asText()).isEqualTo("+237600000000");
        assertThat(data.get("serviceKey").asText()).isEqualTo("audience-deliberer");
        assertThat(data.get("priority").asText()).isEqualTo("STANDARD");
        assertThat(data.get("channels").get(0).asText()).isEqualTo("SMS");
    }

    /** An email adds its subject, as shown in the Guide; the rest is unchanged. */
    @Test
    void un_courriel_ajoute_son_objet_et_rien_d_autre() throws Exception {
        JsonNode data = publier("Objet du courriel").get("data");

        assertThat(data.get("directSubject").asText()).isEqualTo("Objet du courriel");
        assertThat(data.fieldNames()).toIterable()
                .containsExactlyInAnyOrderElementsOf(concat(CHAMPS_MESSAGE, "directSubject"));
    }

    /** Timestamp to the second, with local offset: the observed form, not a UTC instant. */
    @Test
    void l_horodatage_suit_la_forme_observee() throws Exception {
        String temps = publier(null).get("time").asText();

        assertThat(temps).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}([+-]\\d{2}:\\d{2}|Z)");
        assertThat(temps).doesNotContain(".");
    }

    /** W3C trace context: version, 32-char trace, 16-char span, flags. */
    @Test
    void le_contexte_de_trace_est_bien_forme() throws Exception {
        JsonNode enveloppe = publier(null);

        assertThat(enveloppe.get("traceparent").asText()).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
        assertThat(enveloppe.get("traceid").asText()).isEqualTo(enveloppe.get("traceparent").asText());
    }

    /** The reference contains no colon: no accepted message has one. */
    @Test
    void la_reference_ne_porte_aucun_caractere_inhabituel() throws Exception {
        assertThat(publier(null).get("data").get("eventId").asText()).matches("[A-Za-z0-9_.-]+");
    }

    private static List<String> concat(List<String> champs, String ajout) {
        return java.util.stream.Stream.concat(champs.stream(), java.util.stream.Stream.of(ajout)).toList();
    }
}
