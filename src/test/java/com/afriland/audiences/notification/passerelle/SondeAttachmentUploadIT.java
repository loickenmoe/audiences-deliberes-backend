package com.afriland.audiences.notification.passerelle;

import com.afriland.audiences.notification.config.NotificationEngineProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * <b>Manual probe</b>, not a regression test: it talks to the DEV Notification Engine. Runs only if
 * {@code SONDE_ENGINE} is set, and sends <b>no notification to anyone</b>: it only requests a file
 * upload URL, then listens for the response.
 * <p>
 * Attachments use a round trip (Guide V1 section 3 bis): request on
 * {@code attachment-upload-request}, response on {@code attachment-upload-response}. If this topic
 * is silent, the file must be attached to the message itself.
 * <p>
 * Run: {@code SONDE_ENGINE=1 ./mvnw test -Dtest=SondeAttachmentUploadIT}
 */
@EnabledIfEnvironmentVariable(named = "SONDE_ENGINE", matches = ".+")
class SondeAttachmentUploadIT {

    private static final String COURTIER = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "172.21.88.31:32092");
    private static final String TOPIC_DEMANDE = "attachment-upload-request";
    private static final String TOPIC_REPONSE = "attachment-upload-response";
    private static final Duration ATTENTE = Duration.ofSeconds(90);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void demanderUneUrlDeDepotEtEcouterLaReponse() throws Exception {
        String code = exiger("NOTIFICATION_ENGINE_CODE_SERVICE");
        String secret = exiger("NOTIFICATION_ENGINE_JWT_SECRET");
        String requestId = "sonde-" + UUID.randomUUID();

        JetonEngine jeton = new JetonEngine(new NotificationEngineProperties(true, code, secret, 5,
                Duration.ofHours(2), new NotificationEngineProperties.Redirection(false, null, null, null),
                new NotificationEngineProperties.Transport(NotificationEngineProperties.TypeTransport.KAFKA,
                        TOPIC_DEMANDE, "kafka-pubsub", "http://localhost:3500", 10, true, "attachment-upload-request"), null, "STANDARD", false, java.time.Duration.ofMinutes(5)));

        // Content follows Guide section 3 bis, plus the token and service code, the only two fields the
        // engine requires everywhere else.
        Map<String, Object> demande = Map.of(
                "requestId", requestId,
                "fileName", "lettre-constitution.pdf",
                "mimeType", "application/pdf",
                "token", jeton.signer(),
                "serviceKey", code);

        String enveloppe = objectMapper.writeValueAsString(Map.of(
                "id", UUID.randomUUID().toString(),
                "source", code,
                "type", "com.dapr.event.sent",
                "specversion", "1.0",
                "datacontenttype", "application/json",
                "topic", TOPIC_DEMANDE,
                "pubsubname", "kafka-pubsub",
                "time", java.time.OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString(),
                "data", demande));

        try (KafkaConsumer<String, String> consommateur = consommateur();
             KafkaProducer<String, String> producteur = producteur()) {
            consommateur.subscribe(List.of(TOPIC_REPONSE));
            consommateur.poll(Duration.ofSeconds(5)); // forces partition assignment before sending

            producteur.send(new ProducerRecord<>(TOPIC_DEMANDE, requestId, enveloppe)).get();
            System.out.println("[sonde] demande publiée : " + requestId);

            long limite = System.currentTimeMillis() + ATTENTE.toMillis();
            int lus = 0;
            while (System.currentTimeMillis() < limite) {
                ConsumerRecords<String, String> lots = consommateur.poll(Duration.ofSeconds(5));
                for (ConsumerRecord<String, String> message : lots) {
                    lus++;
                    JsonNode charge = objectMapper.readTree(message.value());
                    JsonNode donnees = charge.has("data") ? charge.get("data") : charge;
                    System.out.println("[sonde] réponse lue : requestId=" + donnees.path("requestId").asText()
                            + ", status=" + donnees.path("status").asText()
                            + ", storageKey=" + donnees.path("storageKey").asText()
                            + ", erreur=" + donnees.path("errorMessage").asText());
                    if (requestId.equals(donnees.path("requestId").asText())) {
                        System.out.println("[sonde] réponse reçue pour notre demande, status="
                                + donnees.path("status").asText());
                        assertThat(donnees.path("status").asText()).as("statut de la réponse").isNotBlank();
                        if ("READY".equals(donnees.path("status").asText())) {
                            deposer(donnees.path("presignedPutUrl").asText());
                        }
                        return;
                    }
                }
            }
            fail("[sonde] aucune réponse pour notre demande en " + ATTENTE.toSeconds() + " s. Messages lus sur le topic : " + lus);
        }
    }

    /**
     * File upload via the presigned URL: no byte goes through the engine or Kafka. Checks whether the
     * engine's storage is reachable from a workstation and accepts our upload.
     */
    private void deposer(String urlPresignee) throws Exception {
        byte[] contenu = ("%PDF-1.4" + (char) 10 + "1 0 obj<</Type/Catalog>>endobj" + (char) 10
                + "trailer<</Root 1 0 R>>" + (char) 10 + "%%EOF" + (char) 10)
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        java.net.http.HttpRequest requete = java.net.http.HttpRequest.newBuilder(java.net.URI.create(urlPresignee))
                .header("Content-Type", "application/pdf")
                .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(contenu))
                .build();
        java.net.http.HttpResponse<String> reponse = java.net.http.HttpClient.newHttpClient()
                .send(requete, java.net.http.HttpResponse.BodyHandlers.ofString());
        System.out.println("[sonde] dépôt du fichier : HTTP " + reponse.statusCode()
                + (reponse.statusCode() >= 300 ? " — " + reponse.body() : " (accepté)"));
    }

    private static String exiger(String variable) {
        String valeur = System.getenv(variable);
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalStateException("Variable d'environnement absente : " + variable);
        }
        return valeur;
    }

    private static KafkaProducer<String, String> producteur() {
        Properties proprietes = new Properties();
        proprietes.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, COURTIER);
        proprietes.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        proprietes.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(proprietes);
    }

    private static KafkaConsumer<String, String> consommateur() {
        Properties proprietes = new Properties();
        proprietes.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, COURTIER);
        proprietes.put(ConsumerConfig.GROUP_ID_CONFIG, "sonde-" + UUID.randomUUID());
        proprietes.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        proprietes.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        proprietes.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(proprietes);
    }
}
