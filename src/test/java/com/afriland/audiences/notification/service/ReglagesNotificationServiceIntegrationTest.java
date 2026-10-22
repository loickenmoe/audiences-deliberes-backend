package com.afriland.audiences.notification.service;

import com.afriland.audiences.alerte.domain.CanalAlerte;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.notification.domain.CanalPrestataire;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** Step 4: settings read from a migrated database give exactly the behavior defined by V28. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(ReglagesNotificationService.class)
class ReglagesNotificationServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("audiences_reglages")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private ReglagesNotificationService service;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void les_reglages_initiaux_envoient_Outlook_aux_internes_et_courriel_SMS_aux_prestataires() {
        ReglagesNotification reglages = service.charger();

        for (TypeAlerte type : TypeAlerte.values()) {
            assertThat(reglages.envoie(CanalAlerte.OUTLOOK, type)).as("Outlook " + type).isTrue();
            assertThat(reglages.envoie(CanalAlerte.TEAMS, type)).as("Teams " + type).isFalse();
        }
        for (EvenementPrestataire evenement : EvenementPrestataire.values()) {
            assertThat(reglages.envoie(CanalPrestataire.COURRIEL_PERSONNEL, evenement)).isTrue();
            assertThat(reglages.envoie(CanalPrestataire.SMS, evenement)).isTrue();
            assertThat(reglages.envoie(CanalPrestataire.WHATSAPP, evenement)).as("WhatsApp éteint").isFalse();
        }
    }

    @Test
    void un_reglage_modifie_s_applique_a_la_lecture_suivante() {
        jdbc.update("UPDATE configuration_systeme SET valeur = '' WHERE cle = 'GRILLE_NOTIFICATION_SMS'");

        assertThat(service.charger().envoie(CanalPrestataire.SMS, EvenementPrestataire.RAPPEL_AUDIENCE)).isFalse();
    }
}
