package com.afriland.audiences.notification.repository;

import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.domain.NotificationEnvoi.Origine;
import com.afriland.audiences.notification.domain.StatutEnvoi;
import com.afriland.audiences.notification.domain.TypeDestinataire;
import com.afriland.audiences.notification.passerelle.CanalEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Notification Engine, étape 2 : la migration V27 est appliquée par Flyway sur un PostgreSQL réel,
 * l'entité est validée par Hibernate ({@code ddl-auto=validate}), et les contraintes qui protègent
 * la file tiennent aussi quand on écrit sans passer par l'entité.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class NotificationEnvoiRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("audiences_notification")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private NotificationEnvoiRepository repository;
    @Autowired private JdbcTemplate jdbc;

    private static Origine origine(String eventId) {
        return new Origine(eventId, "RAPPEL_AUDIENCE", 5L, 42L, TypeDestinataire.AVOCAT, 9L);
    }

    @Test
    void une_ligne_a_remettre_est_enregistree_puis_retrouvee_par_son_eventId() {
        repository.saveAndFlush(NotificationEnvoi.aRemettre(origine("alerte-1"), CanalEngine.SMS,
                "237600000000", null, "Audience dans 7 jours."));

        NotificationEnvoi relue = repository.findByEventId("alerte-1-SMS").orElseThrow();
        assertThat(relue.getId()).isNotNull();
        assertThat(relue.getStatut()).isEqualTo(StatutEnvoi.EN_ATTENTE);
        assertThat(relue.getCanal()).isEqualTo(CanalEngine.SMS);
        assertThat(relue.getDestinataireType()).isEqualTo(TypeDestinataire.AVOCAT);
        assertThat(relue.getDateCreation()).isNotNull();
    }

    @Test
    void une_ligne_non_joignable_est_acceptee_sans_destinataire() {
        repository.saveAndFlush(NotificationEnvoi.nonJoignable(origine("alerte-2"), CanalEngine.WHATSAPP, "COORDONNEE_INVALIDE"));

        assertThat(repository.findByEventId("alerte-2-WHATSAPP").orElseThrow().getDestinataire()).isNull();
    }

    @Test
    void l_accuse_positif_efface_le_texte_du_sms_en_base() {
        NotificationEnvoi envoi = repository.saveAndFlush(NotificationEnvoi.aRemettre(origine("alerte-3"),
                CanalEngine.SMS, "237600000000", null, "Texte"));

        envoi.marquerEnvoyee("DELIVERED", LocalDateTime.now());
        repository.saveAndFlush(envoi);

        assertThat(jdbc.queryForObject("SELECT corps FROM notification_envoi WHERE id = ?", String.class, envoi.getId()))
                .isNull();
    }

    @Test
    void un_eventId_deja_present_est_refuse_par_la_base() {
        repository.saveAndFlush(NotificationEnvoi.aRemettre(origine("alerte-4"), CanalEngine.EMAIL, "a@b.cm", "s", "x"));

        assertThatThrownBy(() -> repository.saveAndFlush(
                NotificationEnvoi.aRemettre(origine("alerte-4"), CanalEngine.EMAIL, "c@d.cm", "s", "y")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void la_base_refuse_une_ligne_a_envoyer_sans_destinataire() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO notification_envoi "
                + "(event_id, type_evenement, destinataire_type, canal, statut) "
                + "VALUES ('brut-1', 'T', 'UTILISATEUR', 'EMAIL', 'EN_ATTENTE')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_notification_envoi_joignable");
    }

    // Un test par contrainte : après un refus, PostgreSQL rejette toute la suite de la transaction.
    @Test
    void la_base_refuse_un_canal_inconnu() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO notification_envoi "
                + "(event_id, type_evenement, destinataire_type, canal, destinataire) "
                + "VALUES ('brut-2', 'T', 'UTILISATEUR', 'OUTLOOK', 'a@b.cm')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_notification_envoi_canal");
    }

    @Test
    void la_base_refuse_un_statut_inconnu() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO notification_envoi "
                + "(event_id, type_evenement, destinataire_type, canal, destinataire, statut) "
                + "VALUES ('brut-3', 'T', 'UTILISATEUR', 'EMAIL', 'a@b.cm', 'LIVREE')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_notification_envoi_statut");
    }
}
