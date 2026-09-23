package com.afriland.audiences.notification.passerelle;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Étape 8b : les deux transports réels. On vérifie ce qui part sur le fil — le contrat du Guide — et
 * surtout qu'un échec remonte, pour que le dispatcher retente au lieu de marquer la ligne publiée.
 */
class TransportsTest {

    private static final MessageEngine MESSAGE = MessageEngine.de("jeton", "audience-deliberer", "STANDARD", CanalEngine.SMS, "237600000000",
            null, "Audience dans 7 jours.", "alerte-5-SMS", null);

    // ── Sidecar Dapr ──

    @Test
    void dapr_publie_sur_le_composant_et_le_topic_configures() {
        RestClient.Builder constructeur = RestClient.builder().baseUrl("http://localhost:3500");
        MockRestServiceServer serveur = MockRestServiceServer.bindTo(constructeur).build();
        serveur.expect(requestTo("http://localhost:3500/v1.0/publish/kafka-pubsub/notification-send-request"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").value("jeton"))
                .andExpect(jsonPath("$.channels[0]").value("SMS"))
                .andExpect(jsonPath("$.recipient").value("237600000000"))
                .andExpect(jsonPath("$.eventId").value("alerte-5-SMS"))
                .andExpect(jsonPath("$.category").value("TRANSACTIONAL"))
                .andExpect(jsonPath("$.serviceKey").value("audience-deliberer"))
                .andExpect(jsonPath("$.priority").value("STANDARD"))
                // Q-121 : ni locale ni metadata, aucun message accepté ne les porte.
                .andExpect(jsonPath("$.locale").doesNotExist())
                .andExpect(jsonPath("$.metadata").doesNotExist())
                // Un SMS n'a pas d'objet : le champ ne doit pas être sérialisé.
                .andExpect(jsonPath("$.directSubject").doesNotExist())
                .andRespond(withSuccess());

        PasserelleDapr passerelle = new PasserelleDapr(constructeur.build(), "kafka-pubsub",
                "notification-send-request");

        assertThat(passerelle.publier(MESSAGE)).isEqualTo("DAPR");
        serveur.verify();
    }

    @Test
    void dapr_en_erreur_remonte_pour_que_la_remise_soit_retentee() {
        RestClient.Builder constructeur = RestClient.builder().baseUrl("http://localhost:3500");
        MockRestServiceServer serveur = MockRestServiceServer.bindTo(constructeur).build();
        serveur.expect(requestTo(org.hamcrest.Matchers.containsString("/v1.0/publish/"))).andRespond(withServerError());

        PasserelleDapr passerelle = new PasserelleDapr(constructeur.build(), "pubsub", "topic");

        assertThatThrownBy(() -> passerelle.publier(MESSAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Publication Dapr refusée");
    }

    // ── Kafka direct ──

    @SuppressWarnings("unchecked")
    @Test
    void kafka_depose_le_message_serialise_avec_l_eventId_en_cle() throws Exception {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(eq("notification-send-request"), eq("alerte-5-SMS"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        PasserelleKafka passerelle = new PasserelleKafka(template, new ObjectMapper(), "notification-send-request", 10,
                "kafka-pubsub", "audience-deliberer", false);

        assertThat(passerelle.publier(MESSAGE)).isEqualTo("KAFKA");
        org.mockito.ArgumentCaptor<String> charge = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(template).send(anyString(), anyString(), charge.capture());
        assertThat(charge.getValue()).contains("\"eventId\":\"alerte-5-SMS\"", "\"recipient\":\"237600000000\"")
                .doesNotContain("directSubject", "channelRecipients", "locale", "metadata");
    }

    /**
     * Le moteur consomme ses topics par Dapr : il attend l'enveloppe CloudEvents que le sidecar pose.
     * Faute de sidecar installable, nous la produisons — c'est ce que vérifie ce test.
     */
    @SuppressWarnings("unchecked")
    @Test
    void kafka_reproduit_l_enveloppe_du_sidecar() throws Exception {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        new PasserelleKafka(template, new ObjectMapper(), "notification-send-request", 10,
                "kafka-pubsub", "audience-deliberer", true).publier(MESSAGE);

        org.mockito.ArgumentCaptor<String> charge = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(template).send(anyString(), anyString(), charge.capture());
        com.fasterxml.jackson.databind.JsonNode enveloppe = new ObjectMapper().readTree(charge.getValue());
        assertThat(enveloppe.get("specversion").asText()).isEqualTo("1.0");
        assertThat(enveloppe.get("type").asText()).isEqualTo("com.dapr.event.sent");
        assertThat(enveloppe.get("datacontenttype").asText()).isEqualTo("application/json");
        assertThat(enveloppe.get("topic").asText()).isEqualTo("notification-send-request");
        assertThat(enveloppe.get("pubsubname").asText()).isEqualTo("kafka-pubsub");
        assertThat(enveloppe.get("source").asText()).isEqualTo("audience-deliberer");
        assertThat(enveloppe.get("id").asText()).isNotBlank();
        // Le message métier est intact dans data : c'est lui que le moteur lira après déballage.
        assertThat(enveloppe.get("data").get("eventId").asText()).isEqualTo("alerte-5-SMS");
        assertThat(enveloppe.get("data").get("recipient").asText()).isEqualTo("237600000000");
    }

    /** Sans attente de l'accusé, un courtier injoignable passerait pour un succès. */
    @SuppressWarnings("unchecked")
    @Test
    void kafka_en_echec_remonte() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("courtier injoignable")));

        PasserelleKafka passerelle = new PasserelleKafka(template, new ObjectMapper(), "topic", 1,
                "pubsub", "audience-deliberer", true);

        assertThatThrownBy(() -> passerelle.publier(MESSAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Publication Kafka refusée");
    }
}
