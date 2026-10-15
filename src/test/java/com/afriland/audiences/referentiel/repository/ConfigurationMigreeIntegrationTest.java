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
 * Les valeurs initiales écrites en SQL par les migrations doivent respecter les règles Java des clés
 * (Q-91, Q-118). Une faute de frappe dans un nom d'événement de V28 ne serait sinon découverte qu'au
 * premier enregistrement du DJ, qui se verrait refuser une valeur qu'il n'a pas saisie.
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
                 * **Sans l'ordre, mais sans rien lâcher.** Ce qui est éprouvé est la *couverture* :
                 * aucun événement oublié, aucun inconnu en trop - et l'égalité des ensembles le dit
                 * entièrement. L'ordre, lui, ne veut rien dire : la grille est une liste que la
                 * direction réécrit à sa guise depuis l'écran de configuration, et une valeur
                 * nouvelle s'y ajoute à la suite (V45, V46) sans avoir à être déclarée en dernier
                 * dans son énumération. Exiger l'ordre faisait échouer ce test pour une raison
                 * étrangère à ce qu'il garde.
                 */
                assertThat(lignes.get(regle.name()).getValeur().split(","))
                        .as(regle.name())
                        .containsExactlyInAnyOrderElementsOf(regle.valeursPossibles());
            }
        }
    }

    /** WhatsApp éteint tant que l'exigence de modèles préapprouvés n'est pas levée (plan, points ouverts). */
    @Test
    void les_canaux_prestataires_demarrent_sans_whatsapp() {
        assertThat(repository.findByCle("CANAUX_NOTIFICATION_PRESTATAIRES").orElseThrow().getValeur())
                .isEqualTo("COURRIEL_PERSONNEL,SMS");
        assertThat(repository.findByCle("CANAUX_NOTIFICATION_DEFAUT").orElseThrow().getValeur())
                .isEqualTo("APPLICATIF,OUTLOOK");
    }
}
