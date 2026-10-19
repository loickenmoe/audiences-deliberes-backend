package com.afriland.audiences.referentiel.repository;

import com.afriland.audiences.referentiel.domain.ConfigurationSysteme;
import com.afriland.audiences.referentiel.service.RegleConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Initial values written in SQL by migrations must respect the Java key rules. Otherwise a typo in a
 * V28 event name would only surface on the DJ's first save, refusing a value they never entered.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ConfigurationMigreeIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("audiences_configuration")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private ConfigurationSystemeRepository repository;

    @Test
    void chaque_regle_a_sa_ligne_et_sa_valeur_initiale_est_deja_normalisee() {
        Map<String, String> valeurs = repository.findAll().stream()
                .collect(Collectors.toMap(ConfigurationSysteme::getCle, ConfigurationSysteme::getValeur));

        for (RegleConfiguration regle : RegleConfiguration.values()) {
            assertThat(valeurs).as("ligne migrée pour " + regle).containsKey(regle.name());
            String valeur = valeurs.get(regle.name());
            assertThat(regle.valider(valeur)).as("valeur initiale de " + regle).isEqualTo(valeur);
        }
    }

    @Test
    void les_grilles_couvrent_initialement_tous_les_evenements_de_leur_canal() {
        Map<String, ConfigurationSysteme> lignes = repository.findAll().stream()
                .collect(Collectors.toMap(ConfigurationSysteme::getCle, Function.identity()));

        for (RegleConfiguration regle : RegleConfiguration.values()) {
            if (regle.name().startsWith("GRILLE_NOTIFICATION_")) {
                /*
                 * **Order-insensitive, but strict.** What is tested is *coverage*: no event missing, no unknown
                 * extra, which set equality fully expresses. Order is meaningless: management rewrites the grid
                 * from the configuration screen, and new values are appended (V45, V46) regardless of enum order.
                 */
                assertThat(lignes.get(regle.name()).getValeur().split(","))
                        .as(regle.name())
                        .containsExactlyInAnyOrderElementsOf(regle.valeursPossibles());
            }
        }
    }

    /** WhatsApp off until the pre-approved template requirement is lifted. */
    @Test
    void les_canaux_prestataires_demarrent_sans_whatsapp() {
        assertThat(repository.findByCle("CANAUX_NOTIFICATION_PRESTATAIRES").orElseThrow().getValeur())
                .isEqualTo("COURRIEL_PERSONNEL,SMS");
        assertThat(repository.findByCle("CANAUX_NOTIFICATION_DEFAUT").orElseThrow().getValeur())
                .isEqualTo("APPLICATIF,OUTLOOK");
    }
}
